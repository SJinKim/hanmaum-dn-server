Your goal is to verify the Keycloak auth integration is correctly configured end-to-end.

1. Check Keycloak is reachable at http://localhost:8091 using curl or fetch
2. Verify the backend application.yml has correct `spring.security.oauth2.resourceserver.jwt.issuer-uri`
3. Confirm the Angular environment files have the correct Keycloak realm, clientId, and redirectUri for local vs prod
4. Check the PKCE flow config in the Angular auth module — confirm `responseType: 'code'` and PKCE is enabled
5. Look for any CORS misconfigurations between Angular (ng serve port) and the Spring Boot backend
6. Check that Keycloak roles/scopes referenced in `@PreAuthorize` annotations on the backend match what the realm actually issues
7. Report any mismatches or missing config
