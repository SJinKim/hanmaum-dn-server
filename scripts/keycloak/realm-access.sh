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
        {roles: ([$roles[].name] | sort), groups: ([.groups | walk("")] | sort_by(.path))}
      else error("Managed roles must have URL-safe names and must not be composites yet") end |
      . as $config |
      if (.roles | length == (unique | length)) and (.groups | length == (unique_by(.path) | length))
        and all(.groups[].roles[]; . as $r | $config.roles | index($r) != null) then .
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
    local config="$1" have role group path parent name parent_id endpoint id mappings missing payload
    local group_ids='{}'
    log "application realm roles and groups"
    have="$(realm_access_list roles id,name)" || return
    # kcadm runs through docker exec -i, which can drain standard input. Keep the
    # iteration input on fd 3 so transport calls cannot skip later entries.
    while IFS= read -r -u 3 role; do
        if ! jq -e --arg name "$role" 'any(.name == $name)' <<<"$have" >/dev/null; then
            plan "create realm role $role"
            jq -nc --arg name "$role" '{name: $name}' | kc_write_in create roles -r "$KC_REALM" -f - || return
        fi
    done 3< <(jq -r '.roles[]' <<<"$config")
    # Newly created roles need their server-generated IDs for mapping POSTs.
    if [[ "$APPLY" == "1" ]]; then have="$(realm_access_list roles id,name)" || return; fi

    while IFS= read -r -u 3 group; do
        path="$(jq -r .path <<<"$group")"
        parent="${path%/*}"
        name="${path##*/}"
        parent_id="$(jq -r --arg path "$parent" '.[$path] // empty' <<<"$group_ids")"
        id=''
        endpoint=groups
        if [[ -n "$parent_id" ]]; then endpoint="groups/$parent_id/children"; fi
        if [[ -z "$parent" || -n "$parent_id" ]]; then
            id="$(realm_access_list "$endpoint" id,name | jq -r --arg name "$name" '.[] | select(.name == $name) | .id')" || return
        fi
        if [[ -z "$id" ]]; then
            plan "create group $path"
            if [[ "$APPLY" == "1" ]]; then
                # Ignore all IDs from the local exports. Never move or replace an existing group.
                id="$(jq -nc --arg name "$name" '{name: $name}' | kc create "$endpoint" -r "$KC_REALM" -f - -i)" || return
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
    done 3< <(jq -c '.groups[]' <<<"$config")
}
