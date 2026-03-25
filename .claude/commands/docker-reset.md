Your goal is to completely reset the local Docker infrastructure to a clean state.

⚠️ This will destroy local database data and Keycloak config.

1. Stop and remove all containers: `cd infrastructure && docker-compose down -v`
2. Remove dangling volumes: `docker volume prune -f`
3. Pull latest images: `docker-compose pull`
4. Restart everything: `docker-compose up -d`
5. Wait for healthy status on PostgreSQL and Keycloak
6. Run backend migrations by starting Spring Boot once
7. Confirm all services are reachable and report their URLs
