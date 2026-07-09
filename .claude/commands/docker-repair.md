Your goal is to detect and fix Flyway checksum mismatches in the local Docker backend.

This happens when a migration file (typically a seed file) is modified after it was already
applied to the local DB. Never run this against staging or production.

## Steps

1. Fetch the backend container logs:
   `docker logs hanmaumApp-backend 2>&1 | tail -60`

2. Check for Flyway checksum mismatch errors. Look for lines like:
   ```
   Migration checksum mismatch for migration version <VERSION>
   -> Applied to database : <OLD_CHECKSUM>
   -> Resolved locally    : <NEW_CHECKSUM>
   ```
   If no mismatch is found, report that Flyway is healthy and stop.

3. For each mismatched migration (there may be more than one), run:
   ```
   docker exec hanmaumApp-db bash -c \
     "psql -U \$POSTGRES_USER -d \$POSTGRES_DB -c \
      \"UPDATE flyway_schema_history SET checksum = <NEW_CHECKSUM> WHERE version = '<VERSION>';\""
   ```
   Use the **Resolved locally** checksum as `<NEW_CHECKSUM>` — that is what the JAR expects.

4. Confirm the update:
   ```
   docker exec hanmaumApp-db bash -c \
     "psql -U \$POSTGRES_USER -d \$POSTGRES_DB -c \
      \"SELECT version, checksum FROM flyway_schema_history WHERE version = '<VERSION>';\""
   ```

5. Restart the backend:
   `docker start hanmaumApp-backend`
   Wait 10 seconds, then check the last 10 lines of logs to confirm startup.
   A successful start ends with: `Started ChurchDnBackendApplicationKt in X seconds`

6. If startup fails again, print the new error and stop — do not loop.
