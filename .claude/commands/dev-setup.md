Your goal is to get the full local dev environment running from scratch.

1. Check Docker is running (`docker info`)
2. Start infrastructure: `cd infrastructure && docker-compose up -d`
3. Wait for PostgreSQL (port 5433) and Keycloak (port 8091) to be healthy using `docker-compose ps`
4. Run the Spring Boot backend: `./gradlew bootRun` (in background or new terminal note)
5. Install frontend deps if needed: `cd frontend && npm ci`
6. Start the Angular dev server: `ng serve`
7. Report all running service URLs: backend, frontend, Keycloak admin console
