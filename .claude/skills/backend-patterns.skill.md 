# Backend Patterns

## Package Structure
com.hanmaum.dn.app/
  common/ — config, domainvalue, dto, exception, jpa
  features/<name>/ — domain, repository, service, api/v1/, SomeMappers.kt

## Rules
- @Transactional at service layer only, never controller
- DTOs separate from entities — never expose entity via REST
- @ControllerAdvice for exception handling
- @ResponseStatus on all controller methods
- Repository collections: never null, return empty list
- Test naming: should_<expected>_when_<condition>()

## Security
- Never log JWT, passwords, PII
- No stack traces in API responses
- All input: @Valid + Bean Validation
- JPA/JPQL only — no string-concatenated SQL
