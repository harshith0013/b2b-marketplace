.PHONY: db up down backend frontend test-backend test-frontend verify

db:            ## Start only PostgreSQL (for running backend/frontend from your IDE)
	docker compose up -d postgres

up:            ## Build and start everything
	docker compose up --build

down:          ## Stop everything (data volume is kept)
	docker compose down

backend:       ## Run the API against the local database
	cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local

frontend:      ## Run the web app in dev mode
	cd frontend && npm run dev

test-backend:  ## Unit + integration tests (integration tests need Docker)
	cd backend && mvn -B verify

test-frontend: ## Lint, type-check, unit tests, build
	cd frontend && npm run lint && npm run typecheck && npm test && npm run build

verify: test-backend test-frontend
