# Production backup preparation

Do this **1–2 weeks before the first production launch**, not during normal
feature development.

1. Rent the smallest suitable offsite Storage Box.
2. Install `restic` on the production host and configure SSH host verification.
3. Copy `backup.env.example` to `/opt/hanmaum-dn-server/.env.backup`.
4. Store the Restic password at the configured root-only path.
5. Set `BACKUP_ENABLED=true`, repository, databases, retention, and schedule.
6. Run `backup.sh` manually.
7. Run `restore-test.sh` into an isolated database.
8. Start the restored backend with a separately secured copy of the production
   PII keyring and verify profile decryption.
9. After the first production PII backfill completes with `remaining=0`, set
   `PII_LEGACY_PLAINTEXT_READ_ENABLED=false`.
10. Install the dynamic systemd timer.

The production PII keyring must be backed up separately from Restic. Never put
the keyring and its database backup in the same credential boundary.
