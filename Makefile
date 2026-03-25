# Makefile
up:
	docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml up -d

build-backend:
	docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml up -d --build backend

logs:
	docker compose -p infrastructure --project-directory . -f infrastructure/docker-compose.yml logs -f