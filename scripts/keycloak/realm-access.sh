#!/usr/bin/env bash
# Shared by configure-realm.sh and the transport-level regression tests.
# Uses the caller's kc, kc_write_in and plan functions; never imports a whole realm.

realm_access_config() {
    local primary secondary filter
    filter='
      def managed_role: startswith("default-roles-") or . == "offline_access" or . == "uma_authorization" | not;
      def walk($parent): .[] | . as $g | ($parent + "/" + .name) as $path |
        if (.name | test("^[A-Za-z0-9_-]+$")) and (($g.path // $path) == $path)
          and (($g.clientRoles // {}) | length == 0) then
          {path: $path, roles: (($g.realmRoles // []) | sort)}, (($g.subGroups // []) | walk($path))
        else error("Unsupported group name, path or client-role mapping") end;
      [.roles.realm[] | select(.name | managed_role)] as $roles |
      if all($roles[]; (.name | test("^[A-Za-z0-9_:-]+$")) and ((.composite // false) == false)) then
        {roles: ([$roles[] | {name, description: (.description // "")}] | sort_by(.name)), groups: ([.groups | walk("")] | sort_by(.path))}
      else error("Managed roles must have URL-safe names and must not be composites yet") end |
      . as $config |
      if (.roles | length == (unique_by(.name) | length)) and (.groups | length == (unique_by(.path) | length))
        and all(.groups[].roles[]; . as $r | $config.roles | map(.name) | index($r) != null) then .
      else error("Duplicate role/group or undefined group-role mapping") end'
    primary="$(jq -ceS "$filter" "$1")" || return
    secondary="$(jq -ceS "$filter" "$2")" || return
    if [[ "$primary" != "$secondary" ]]; then
        echo "Realm exports disagree on managed roles or group mappings; reconcile both exports first" >&2
        return 1
    fi
    printf '%s\n' "$primary"
}

# Paginate direct siblings rather than relying on the truncated nested group representation.
realm_access_list() {
    local endpoint="$1" fields="$2" first=0 page result='[]' size=100
    while :; do
        page="$(kc get "$endpoint" -r "$KC_REALM" -q first="$first" -q max="$size" --fields "$fields" </dev/null)" || return
        result="$(jq -c --argjson page "$page" '. + $page' <<<"$result")" || return
        if [[ "$(jq 'length' <<<"$page")" -lt "$size" ]]; then break; fi
        first=$((first + size))
    done
    printf '%s\n' "$result"
}

ensure_realm_access() {
    local config="$1" have role group path parent name parent_id endpoint id mappings missing payload roles groups siblings current
    local siblings_by_parent='{}'
    local group_ids='{}'
    # Materialize both lists before any I/O: process-substitution errors would be lost.
    roles="$(jq -c '.roles | if type == "array" then .[] else error("roles must be an array") end' <<<"$config")" || return
    groups="$(jq -c '.groups | if type == "array" then .[] else error("groups must be an array") end' <<<"$config")" || return
    log "application realm roles and groups"
    have="$(realm_access_list roles id,name,description)" || return
    # kcadm runs through docker exec -i, which can drain standard input. Keep the
    # iteration input on fd 3 so transport calls cannot skip later entries.
    while IFS= read -r -u 3 role; do
        [[ -n "$role" ]] || continue
        name="$(jq -r .name <<<"$role")"
        current="$(jq -c --arg name "$name" '.[] | select(.name == $name)' <<<"$have")"
        if [[ -z "$current" ]]; then
            plan "create realm role $name"
            kc_write_in create roles -r "$KC_REALM" -f - <<<"$role" || return
        elif ! jq -e --argjson target "$role" '(.description // "") == $target.description' <<<"$current" >/dev/null; then
            plan "update realm role description $name"
            kc_write_in update "roles/$name" -r "$KC_REALM" -f - <<<"$role" || return
        fi
    done 3<<<"$roles"
    # Newly created roles need their server-generated IDs for mapping POSTs.
    if [[ "$APPLY" == "1" ]]; then have="$(realm_access_list roles id,name,description)" || return; fi

    while IFS= read -r -u 3 group; do
        [[ -n "$group" ]] || continue
        path="$(jq -r .path <<<"$group")"
        parent="${path%/*}"
        name="${path##*/}"
        parent_id="$(jq -r --arg path "$parent" '.[$path] // empty' <<<"$group_ids")"
        id=''
        endpoint=groups
        if [[ -n "$parent_id" ]]; then endpoint="groups/$parent_id/children"; fi
        if [[ -z "$parent" || -n "$parent_id" ]]; then
            if ! jq -e --arg parent "$parent" 'has($parent)' <<<"$siblings_by_parent" >/dev/null; then
                siblings="$(realm_access_list "$endpoint" id,name)" || return
                siblings_by_parent="$(jq -c --arg parent "$parent" --argjson siblings "$siblings" '.[$parent] = $siblings' <<<"$siblings_by_parent")"
            fi
            id="$(jq -r --arg parent "$parent" --arg name "$name" '.[$parent][] | select(.name == $name) | .id' <<<"$siblings_by_parent")" || return
        fi
        if [[ -z "$id" ]]; then
            plan "create group $path"
            if [[ "$APPLY" == "1" ]]; then
                # Ignore all IDs from the local exports. Never move or replace an existing group.
                id="$(jq -nc --arg name "$name" '{name: $name}' | kc create "$endpoint" -r "$KC_REALM" -f - -i)" || return
                siblings_by_parent="$(jq -c --arg parent "$parent" --arg name "$name" --arg id "$id" '.[$parent] += [{id: $id, name: $name}]' <<<"$siblings_by_parent")"
            fi
        fi
        group_ids="$(jq -c --arg path "$path" --arg id "$id" '.[$path] = $id' <<<"$group_ids")"
        mappings='[]'
        if [[ -n "$id" ]]; then
            mappings="$(kc get "groups/$id/role-mappings/realm" -r "$KC_REALM" --fields name </dev/null)" || return
        fi
        missing="$(jq -c --argjson have "$mappings" '.roles - [$have[].name]' <<<"$group")"
        if [[ "$missing" != '[]' ]]; then
            plan "map $(jq -r 'join(", ")' <<<"$missing") to $path"
            payload="$(jq -c --argjson names "$missing" '[.[] | select(.name as $name | $names | index($name))]' <<<"$have")"
            if [[ "$APPLY" == "1" ]]; then
                if [[ "$(jq 'length' <<<"$payload")" != "$(jq 'length' <<<"$missing")" ]]; then
                    echo "Cannot resolve all realm role IDs for $path" >&2
                    return 1
                fi
                kc_write_in create "groups/$id/role-mappings/realm" -r "$KC_REALM" -f - <<<"$payload" || return
            fi
        fi
    done 3<<<"$groups"
}
