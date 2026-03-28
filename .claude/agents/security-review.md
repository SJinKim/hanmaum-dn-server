# Security Review Agent

Use when: before any PR touching auth, API endpoints, or database access.

Check:
- JWT/credentials never logged
- No hardcoded secrets in any file
- All new endpoints have @PreAuthorize
- Input validation (@Valid) on all request bodies
- No raw SQL / string concatenation
- stack-trace exposure in error responses

Return: PASS / FAIL per check with file:line references.
