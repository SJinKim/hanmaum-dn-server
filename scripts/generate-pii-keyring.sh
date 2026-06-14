#!/usr/bin/env sh
set -eu

output_path="${1:-.local-secrets/hanmaum-pii-keyring.properties}"
key_id="${PII_ACTIVE_KEY_ID:-v1}"

umask 077
mkdir -p "$(dirname "$output_path")"

cat > "$output_path" <<EOF
active-key-id=$key_id
key.$key_id=$(openssl rand -base64 32 | tr -d '\n')
index-key=$(openssl rand -base64 32 | tr -d '\n')
EOF

chmod 600 "$output_path"
printf 'Created PII keyring: %s\n' "$output_path"
printf 'Back it up separately from the database. Losing it makes encrypted PII unrecoverable.\n'
