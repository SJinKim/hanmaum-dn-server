Your goal is to detect any mismatches between the Angular API services and the Spring Boot controllers.

1. Collect all `@RestController` endpoints from the backend — method, path, request/response types
2. Collect all HttpClient calls from Angular services — method, path, expected request/response types
3. Compare them side-by-side and flag:
   - Paths that exist in the backend but are not called by the frontend
   - Paths called by the frontend that don't exist in the backend
   - Type mismatches between backend DTOs and Angular interfaces
4. Output a table: | Endpoint | Backend | Frontend | Status |
