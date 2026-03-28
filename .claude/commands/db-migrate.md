Your goal is to apply and verify database migrations safely.

1. Check the current migration state using the JPA/Flyway or Liquibase status command
2. Run `./gradlew bootRun --args='--spring.jpa.hibernate.ddl-auto=validate'` to dry-validate the schema
3. Apply migrations by starting the app normally: `./gradlew bootRun`
4. Confirm no schema drift by checking migration table (flyway_schema_history or databasechangelog)
5. If any migration fails, print the exact SQL error and suggest a fix
