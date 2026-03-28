Your goal is to audit the backend for common issues.

Do the following:

1. Run `./gradlew build` and surface any compilation errors or warnings
2. Run `./gradlew test` and report failing tests with full stack traces
3. Check for any `@Transactional` methods that are public but called internally (self-invocation proxy bypass)
4. Scan for N+1 query risks: JPA entities with @OneToMany or @ManyToMany that lack fetch strategy annotations
5. Look for hardcoded secrets or credentials in .kt, .yml, or .properties files
6. Report all findings grouped by severity: Critical / Warning / Info
