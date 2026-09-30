#!/usr/bin/env bash
# Brings one deployed realm to the target state from KEYCLOAK_ENVIRONMENTS.md (#235).
#
# Runs on the host that runs the Keycloak container and talks to it through kcadm inside
# the container. Dry run by default; APPLY=1 writes. Re-running is safe: every step first
# reads the current state and only changes what differs.
#
# Required:
#   KC_REALM          hanmaum-dn-st | hanmaum-dn-prod
#   DASHBOARD_URLS    comma-separated HTTPS origins of the admin dashboard,
#                     e.g. https://dn-admin-dashboard.st.graceops.de. Set it empty
#                     (DASHBOARD_URLS=) while an environment has no dashboard: the
#                     dashboard client then accepts no redirect, so no one can log in there.
#                     The first origin also becomes the dashboard client's base URL, which
#                     the login theme links from its error and expired-link pages (#243).
# Optional:
#   APPLY=1                      write changes (default: dry run)
#   MOBILE_REDIRECT_URIS         default com.hanmaum.dn.mobile:/oauth2redirect
#   MOBILE_DIRECT_GRANTS         default true: the app logs in with its own password form
#   VERIFY_USERNAME              user for the example access token check (aud and iss)
#   EXPECTED_ISSUER              default https://auth.graceops.de/realms/$KC_REALM
#   KC_CONTAINER                 default hanmaumApp-keycloak
#   KC_ADMIN_USER                admin in the master realm; kcadm prompts for the password.
#                                Without it the container's bootstrap admin env is used, so
#                                the password never leaves the container.
set -euo pipefail

: "${KC_REALM:?KC_REALM must be set (hanmaum-dn-st or hanmaum-dn-prod)}"
: "${DASHBOARD_URLS?DASHBOARD_URLS must be set (comma-separated HTTPS origins, or empty)}"
APPLY="${APPLY:-0}"
MOBILE_REDIRECT_URIS="${MOBILE_REDIRECT_URIS:-com.hanmaum.dn.mobile:/oauth2redirect}"
MOBILE_DIRECT_GRANTS="${MOBILE_DIRECT_GRANTS:-true}"
VERIFY_USERNAME="${VERIFY_USERNAME:-}"
EXPECTED_ISSUER="${EXPECTED_ISSUER:-https://auth.graceops.de/realms/$KC_REALM}"
KC_CONTAINER="${KC_CONTAINER:-hanmaumApp-keycloak}"
KC_ADMIN_USER="${KC_ADMIN_USER:-}"

readonly API_AUDIENCE="hanmaum-dn-api"
readonly AUDIENCE_SCOPE="hanmaum-dn-api-audience"
readonly DASHBOARD_CLIENT="hanmaum-dashboard"
readonly MOBILE_CLIENT="hanmaum-mobile"
readonly BACKEND_CLIENT="dn-backend-admin"
# MemberService creates users and sends the verification mail, MemberPurgeService deletes
# users. Nothing else in the backend calls the admin API, so manage-users is enough.
readonly BACKEND_ROLES=("manage-users")
# Login theme from infrastructure/docker/keycloak/themes (#243); Korean first, English second.
readonly LOGIN_THEME="hanmaum"
readonly REALM_LOCALES='["ko","en"]'
readonly REALM_DEFAULT_LOCALE="ko"
readonly KCADM_CONFIG="/tmp/kcadm-hdn-235.config"

command -v jq >/dev/null || { echo "jq is required" >&2; exit 1; }
[[ "$MOBILE_DIRECT_GRANTS" == "true" || "$MOBILE_DIRECT_GRANTS" == "false" ]] \
    || { echo "MOBILE_DIRECT_GRANTS must be true or false" >&2; exit 1; }

changes=0

log() { printf '%s\n' "$*"; }
plan() {
    changes=$((changes + 1))
    if [[ "$APPLY" == "1" ]]; then log "  apply: $*"; else log "  would: $*"; fi
}

kc() { docker exec -i "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh "$@" --config "$KCADM_CONFIG"; }
kc_write() { if [[ "$APPLY" == "1" ]]; then kc "$@" >/dev/null; fi; }
# Same for writes that read a body from stdin. The dry run must still drain it, or the
# producer (jq) dies of SIGPIPE and pipefail ends the script.
kc_write_in() { if [[ "$APPLY" == "1" ]]; then kc "$@" >/dev/null; else cat >/dev/null; fi; }

cleanup() { docker exec "$KC_CONTAINER" rm -f "$KCADM_CONFIG" >/dev/null 2>&1 || true; }
trap cleanup EXIT

login() {
    if [[ -n "$KC_ADMIN_USER" ]]; then
        docker exec -it "$KC_CONTAINER" /opt/keycloak/bin/kcadm.sh config credentials \
            --config "$KCADM_CONFIG" --server http://localhost:8090 --realm master --user "$KC_ADMIN_USER"
    else
        docker exec "$KC_CONTAINER" sh -c \
            '[ -n "$KC_BOOTSTRAP_ADMIN_USERNAME" ] || { echo "no bootstrap admin in container, set KC_ADMIN_USER" >&2; exit 1; }
             /opt/keycloak/bin/kcadm.sh config credentials --config "$0" --server http://localhost:8090 \
                 --realm master --user "$KC_BOOTSTRAP_ADMIN_USERNAME" --password "$KC_BOOTSTRAP_ADMIN_PASSWORD"' \
            "$KCADM_CONFIG"
    fi
}

# Comma list → JSON array of strings; each origin also yields "<origin>/*" as redirect.
json_list() { jq -cn --arg s "$1" '$s | split(",") | map(gsub("^\\s+|\\s+$"; "")) | map(select(. != ""))'; }
dashboard_origins="$(json_list "$DASHBOARD_URLS" | jq -c 'map(sub("/$"; ""))')"
dashboard_redirects="$(jq -c 'map(. + "/*")' <<<"$dashboard_origins")"
dashboard_base_url="$(jq -r 'if length > 0 then .[0] + "/" else "" end' <<<"$dashboard_origins")"
mobile_redirects="$(json_list "$MOBILE_REDIRECT_URIS")"

if jq -e 'map(select(startswith("https://") | not)) | length > 0' <<<"$dashboard_origins" >/dev/null; then
    echo "DASHBOARD_URLS must all be https:// origins in a deployed realm" >&2
    exit 1
fi
if [[ "$dashboard_origins" == "[]" ]]; then
    log "DASHBOARD_URLS is empty: $DASHBOARD_CLIENT gets no redirect URIs and no web origins"
fi

client_json() { kc get clients -r "$KC_REALM" -q clientId="$1" | jq -c '.[0] // empty'; }

ensure_audience_scope() {
    log "client scope $AUDIENCE_SCOPE"
    local scope mapper
    scope="$(kc get client-scopes -r "$KC_REALM" | jq -c --arg n "$AUDIENCE_SCOPE" '.[] | select(.name == $n)')"
    mapper="$(jq -nc --arg aud "$API_AUDIENCE" '{
        name: "api audience", protocol: "openid-connect", protocolMapper: "oidc-audience-mapper",
        config: {"included.custom.audience": $aud, "access.token.claim": "true",
                 "id.token.claim": "false", "introspection.token.claim": "true"}}')"
    if [[ -z "$scope" ]]; then
        plan "create scope with audience $API_AUDIENCE in access tokens"
        jq -c --argjson m "$mapper" -n --arg n "$AUDIENCE_SCOPE" '{
            name: $n, protocol: "openid-connect",
            attributes: {"include.in.token.scope": "false", "display.on.consent.screen": "false"},
            protocolMappers: [$m]}' | kc_write_in create client-scopes -r "$KC_REALM" -f -
        return
    fi
    local id
    id="$(jq -r .id <<<"$scope")"
    if ! jq -e --arg aud "$API_AUDIENCE" \
        '(.protocolMappers // []) | any(.protocolMapper == "oidc-audience-mapper"
            and .config["included.custom.audience"] == $aud and .config["access.token.claim"] == "true")' \
        <<<"$scope" >/dev/null; then
        plan "add audience mapper $API_AUDIENCE to existing scope"
        kc_write_in create "client-scopes/$id/protocol-mappers/models" -r "$KC_REALM" -f - <<<"$mapper"
    fi
}

scope_id() {
    kc get client-scopes -r "$KC_REALM" | jq -r --arg n "$AUDIENCE_SCOPE" '.[] | select(.name == $n) | .id'
}

# The theme must be on the server before a realm points at it, or its login pages break.
# 비밀번호 찾기 on the login page needs resetPasswordAllowed.
ensure_realm_login_pages() {
    log "realm login theme $LOGIN_THEME, locales $REALM_LOCALES (default $REALM_DEFAULT_LOCALE), password reset"
    if ! kc get serverinfo | jq -e --arg t "$LOGIN_THEME" '.themes.login | any(.name == $t)' >/dev/null; then
        log "  missing: theme $LOGIN_THEME is not mounted in $KC_CONTAINER (docker-compose.prod.yml). Not set."
        changes=$((changes + 1))
        return
    fi
    local realm drift
    realm="$(kc get "realms/$KC_REALM" --fields loginTheme,internationalizationEnabled,supportedLocales,defaultLocale,resetPasswordAllowed)"
    drift="$(jq -c --arg t "$LOGIN_THEME" --argjson l "$REALM_LOCALES" --arg d "$REALM_DEFAULT_LOCALE" '[
        (select(.loginTheme != $t) | "loginTheme"),
        (select(.internationalizationEnabled != true) | "internationalizationEnabled"),
        (select((.supportedLocales // []) | sort != ($l | sort)) | "supportedLocales"),
        (select(.defaultLocale != $d) | "defaultLocale"),
        (select(.resetPasswordAllowed != true) | "resetPasswordAllowed") ]' <<<"$realm")"
    [[ "$drift" != "[]" ]] || return 0
    plan "update $(jq -r 'join(", ")' <<<"$drift")"
    kc_write update "realms/$KC_REALM" -s loginTheme="$LOGIN_THEME" -s internationalizationEnabled=true \
        -s supportedLocales="$REALM_LOCALES" -s defaultLocale="$REALM_DEFAULT_LOCALE" -s resetPasswordAllowed=true
}

# $1 clientId, $2 redirects JSON, $3 web origins JSON, $4 direct grants true|false,
# $5 base URL (optional; empty leaves the stored one alone)
ensure_user_client() {
    local client_id="$1" redirects="$2" origins="$3" direct="$4" base_url="${5:-}"
    log "client $client_id"
    local target client id
    target="$(jq -nc --arg c "$client_id" --argjson r "$redirects" --argjson o "$origins" --argjson d "$direct" \
        --arg b "$base_url" '{
        clientId: $c, enabled: true, protocol: "openid-connect",
        publicClient: true, bearerOnly: false, standardFlowEnabled: true, implicitFlowEnabled: false,
        directAccessGrantsEnabled: $d, serviceAccountsEnabled: false,
        redirectUris: $r, webOrigins: $o,
        attributes: {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": "+",
                     "oauth2.device.authorization.grant.enabled": "false",
                     "oidc.ciba.grant.enabled": "false"}}
        + (if $b == "" then {} else {baseUrl: $b} end)')"
    client="$(client_json "$client_id")"
    if [[ -z "$client" ]]; then
        plan "create public client (PKCE S256, direct grants $direct)"
        kc_write_in create clients -r "$KC_REALM" -f - <<<"$target"
    else
        id="$(jq -r .id <<<"$client")"
        local drift
        drift="$(jq -nc --argjson have "$client" --argjson want "$target" '
            [ ($want | to_entries[] | select(.key != "attributes" and .key != "clientId")
                | select(($have[.key] | if type == "array" then sort else . end)
                         != (.value | if type == "array" then sort else . end)) | .key),
              ($want.attributes | to_entries[] | select($have.attributes[.key] != .value) | "attributes." + .key) ]')"
        if [[ "$drift" != "[]" ]]; then
            plan "update $(jq -r 'join(", ")' <<<"$drift")"
            # Merge target into the stored client; attributes not named here stay as they are.
            jq -c --argjson want "$target" '. * $want | .redirectUris = $want.redirectUris | .webOrigins = $want.webOrigins' \
                <<<"$client" | kc_write_in update "clients/$id" -r "$KC_REALM" -f -
        fi
    fi

    [[ "$APPLY" == "1" || -n "$client" ]] || { plan "attach $AUDIENCE_SCOPE as default scope"; return; }
    client="$(client_json "$client_id")"
    [[ -n "$client" ]] || return
    id="$(jq -r .id <<<"$client")"
    if ! kc get "clients/$id/default-client-scopes" -r "$KC_REALM" \
        | jq -e --arg n "$AUDIENCE_SCOPE" 'any(.name == $n)' >/dev/null; then
        plan "attach $AUDIENCE_SCOPE as default scope"
        local sid
        sid="$(scope_id)"
        if [[ -n "$sid" ]]; then kc_write update "clients/$id/default-client-scopes/$sid" -r "$KC_REALM"; fi
    fi
}

ensure_backend_client() {
    log "client $BACKEND_CLIENT"
    local client id
    client="$(client_json "$BACKEND_CLIENT")"
    if [[ -z "$client" ]]; then
        log "  missing: create it in the Admin Console (confidential, service account only) and put"
        log "  its secret into KEYCLOAK_BACKEND_CLIENT_SECRET on the server. Not created here."
        changes=$((changes + 1))
        return
    fi
    id="$(jq -r .id <<<"$client")"
    if ! jq -e '.publicClient == false and .serviceAccountsEnabled == true and .standardFlowEnabled == false
                and .implicitFlowEnabled == false and .directAccessGrantsEnabled == false' <<<"$client" >/dev/null; then
        plan "restrict to client_credentials (no browser, implicit or password flow)"
        jq -c '.publicClient = false | .serviceAccountsEnabled = true | .standardFlowEnabled = false
               | .implicitFlowEnabled = false | .directAccessGrantsEnabled = false | .redirectUris = []
               | .webOrigins = []' <<<"$client" | kc_write_in update "clients/$id" -r "$KC_REALM" -f -
    fi

    local sa have
    sa="$(kc get "clients/$id/service-account-user" -r "$KC_REALM" | jq -r .username)"
    have="$(kc get-roles -r "$KC_REALM" --uusername "$sa" --cclientid realm-management | jq -c '[.[].name]')"
    local role
    # Grant first, revoke second, so the backend never runs without the rights it needs.
    for role in "${BACKEND_ROLES[@]}"; do
        if ! jq -e --arg r "$role" 'index($r)' <<<"$have" >/dev/null; then
            plan "grant realm-management/$role to $sa"
            kc_write add-roles -r "$KC_REALM" --uusername "$sa" --cclientid realm-management --rolename "$role"
        fi
    done
    for role in $(jq -r --argjson keep "$(printf '%s\n' "${BACKEND_ROLES[@]}" | jq -R . | jq -sc .)" \
        '.[] | select(. as $r | $keep | index($r) | not)' <<<"$have"); do
        plan "revoke realm-management/$role from $sa"
        kc_write remove-roles -r "$KC_REALM" --uusername "$sa" --cclientid realm-management --rolename "$role"
    done
}

verify_tokens() {
    [[ -n "$VERIFY_USERNAME" ]] || { log "token check skipped (VERIFY_USERNAME not set)"; return; }
    local uid client id token ok=0
    uid="$(kc get users -r "$KC_REALM" -q username="$VERIFY_USERNAME" -q exact=true | jq -r '.[0].id // empty')"
    [[ -n "$uid" ]] || { echo "token check: user $VERIFY_USERNAME not found" >&2; return 1; }
    for client in "$DASHBOARD_CLIENT" "$MOBILE_CLIENT"; do
        id="$(client_json "$client" | jq -r '.id // empty')"
        [[ -n "$id" ]] || { log "token $client: client missing"; ok=1; continue; }
        # Example token from the admin API; claims only, no signature is printed.
        token="$(kc get "clients/$id/evaluate-scopes/generate-example-access-token" -r "$KC_REALM" \
            -q scope=openid -q userId="$uid")"
        if jq -e --arg aud "$API_AUDIENCE" --arg iss "$EXPECTED_ISSUER" \
            '(.aud | if type == "array" then . else [.] end | index($aud)) and .iss == $iss' <<<"$token" >/dev/null; then
            log "token $client: aud contains $API_AUDIENCE, iss $EXPECTED_ISSUER"
        else
            log "token $client: MISMATCH $(jq -c '{aud, iss, azp}' <<<"$token")"
            ok=1
        fi
    done
    return "$ok"
}

log "realm $KC_REALM on $KC_CONTAINER ($([[ "$APPLY" == "1" ]] && echo apply || echo dry run))"
login
kc get "realms/$KC_REALM" --fields realm >/dev/null

ensure_realm_login_pages
ensure_audience_scope
ensure_user_client "$DASHBOARD_CLIENT" "$dashboard_redirects" "$dashboard_origins" false "$dashboard_base_url"
# Native app: no CORS, no browser origin.
ensure_user_client "$MOBILE_CLIENT" "$mobile_redirects" "[]" "$MOBILE_DIRECT_GRANTS"
ensure_backend_client

log "$changes change(s) $([[ "$APPLY" == "1" ]] && echo applied || echo pending)"
if [[ "$APPLY" == "1" || "$changes" == "0" ]]; then verify_tokens; fi
