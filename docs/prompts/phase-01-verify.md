# Prompt: verify and finish Phase 1

Read CLAUDE.md and docs/ARCHITECTURE.md.

The backend was written without access to Maven Central and has never been compiled.

1. Check the latest Spring Boot 4.x patch release that is at least 14 days old and update the
   parent version in backend/pom.xml.
2. Run `cd backend && mvn -B verify`. Fix compile and test failures. Likely causes: Boot 4
   starter names, moved packages, Testcontainers 2 artifact or class names. Keep the intent of
   every test; do not delete or weaken assertions to make them pass.
3. Run `mvn spotless:apply`, then `mvn spotless:check`.
4. Run the frontend checks and Playwright tests.
5. Run `docker compose up --build` and confirm http://localhost:3000 shows the server as Working.

Report: files changed, commands run with results, anything still failing. Do not start Phase 2.
