Your goal is to scaffold a new full-stack feature end-to-end.

The feature name is: $ARGUMENTS

Do the following:

1. **Backend**: Create a JPA entity, repository, service, and REST controller for this feature under the appropriate package
2. **Backend**: Add a Flyway/Liquibase migration script for any new tables or columns
3. **Backend**: Add unit tests for the service layer and integration tests for the controller
4. **Frontend**: Create an Angular feature module (or standalone component set) with a list view and detail/form view
5. **Frontend**: Create a typed API service using HttpClient that maps to the backend endpoints
6. **Frontend**: Use PrimeNG components (p-table, p-dialog, p-button) and Tailwind for layout
7. **Frontend**: Wire up Keycloak route guards if the feature requires authentication
8. Print a summary of all created files