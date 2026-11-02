# B2B Marketplace (working name)

AI-powered, privacy-first B2B wholesale and surplus clearance marketplace for India.
See `docs/ARCHITECTURE.md` for the architecture, phase plan, risks and proposed data model.

## Prerequisites
- Java 21 and Maven 3.9+
- Node.js 24 (ships npm 11; npm 10 has a resolver bug with this project's dependencies)
- Docker Desktop (for PostgreSQL and the backend integration tests)

## Quick start

Everything in Docker:
```bash
docker compose up --build
# Web: http://localhost:3000   API health: http://localhost:8080/actuator/health
```

Or run the apps from your editor with only the database in Docker:
```bash
docker compose up -d postgres
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd frontend && npm install && npm run dev
```

## Tests
```bash
cd backend && mvn verify              # unit + integration (Testcontainers, needs Docker)
cd frontend && npm run lint && npm run typecheck && npm test && npm run build
cd frontend && npx playwright install chromium && npm run test:e2e
```

## Layout
```
backend/     Spring Boot modular monolith (com.b2bmarket.<module>)
frontend/    Next.js App Router app; pages under src/app/[lang] (en, te, hi)
docs/        Architecture and phase prompts
CLAUDE.md    Project rules read automatically by Claude Code
```

## Phase 1 status
Verified locally on 2026-11-02 (Spring Boot 4.1.1, JDK 21, Node 24):
- Backend: `mvn verify` passes (5 unit tests, 6 Testcontainers integration tests);
  `spotless:check` is clean.
- Frontend: lint, typecheck, 17 unit tests, production build and 6 Playwright tests pass.
- `docker compose up --build`: all three containers healthy; the home page shows the server
  as Working.
- Telugu and Hindi strings need review by a native speaker.

If Homebrew's default JDK is newer than 21, run Maven with
`JAVA_HOME=$(brew --prefix openjdk@21)`.
