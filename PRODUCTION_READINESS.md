# Production readiness

## Mandatory 1–2 weeks before production launch

- [ ] Rent and connect an offsite Storage Box.
- [ ] Create `/opt/hanmaum-dn-server/.env.backup` from the provided example.
- [ ] Set the desired dynamic backup schedule and 30-day retention.
- [ ] Run one encrypted backup of both application and Keycloak databases.
- [ ] Restore into an isolated database and verify PII decryption.
- [ ] Store an offline encrypted copy of the production PII keyring.
- [ ] After the initial PII backfill reports `remaining=0`, set
      `PII_LEGACY_PLAINTEXT_READ_ENABLED=false` and restart once.
- [ ] Enable and inspect `hanmaum-backup.timer`.

Production deployment intentionally checks for an enabled backup configuration.
Do not remove that gate; completing this checklist is the release condition.
