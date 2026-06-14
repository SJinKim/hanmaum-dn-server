#!/usr/bin/env sh
set -eu

deploy_dir="${DEPLOY_DIR:-/opt/hanmaum-dn-server}"
env_file="${BACKUP_ENV_FILE:-$deploy_dir/.env.backup}"

test "$(id -u)" -eq 0 || {
  echo "Run as root to install the backup timer." >&2
  exit 1
}
test -f "$env_file"

set -a
# shellcheck disable=SC1090
. "$env_file"
set +a

: "${BACKUP_ON_CALENDAR:?BACKUP_ON_CALENDAR is required}"

cat > /etc/systemd/system/hanmaum-backup.service <<EOF
[Unit]
Description=Hanmaum encrypted PostgreSQL offsite backup
Requires=docker.service
After=docker.service network-online.target

[Service]
Type=oneshot
EnvironmentFile=$env_file
WorkingDirectory=$deploy_dir
ExecStart=$deploy_dir/infrastructure/backup/backup.sh
EOF

cat > /etc/systemd/system/hanmaum-backup.timer <<EOF
[Unit]
Description=Schedule Hanmaum encrypted PostgreSQL backups

[Timer]
OnCalendar=$BACKUP_ON_CALENDAR
Persistent=true
RandomizedDelaySec=10m

[Install]
WantedBy=timers.target
EOF

systemctl daemon-reload
systemctl enable --now hanmaum-backup.timer
systemctl list-timers hanmaum-backup.timer
