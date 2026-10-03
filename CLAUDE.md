# Crate

Letterboxd for albums. Microservices portfolio project:
Java 25, Spring Boot 4.1, Spring Cloud 2025.1, Kafka 4 (KRaft), PostgreSQL 17, React + TS.

Read docs/SPEC.md before any task. Current phase: **Phase 0**.

## Rules
- Build only what the current phase needs. Ask before adding dependencies.
- I'm learning: explain concepts (security, Kafka, transactions, concurrency) before
  implementing them, and summarize what changed afterwards.
- Spring Boot 4 + Jackson 3 + Spring Cloud 2025.1: verify starter names, packages and
  property prefixes; don't assume Boot 3.
- Every endpoint and consumer has a Testcontainers integration test.
  Never call real external APIs in tests.
- A service never touches another service's DB. Only libs/event-contracts is shared.
- No secrets in git. Conventional commits.
- Never add Co-Authored-By trailers or "Generated with Claude Code" lines to commit messages.

## Commands
- make infra-up / make infra-down
- ./gradlew build
- ./gradlew :services:<name>:bootRun --args='--spring.profiles.active=local'
