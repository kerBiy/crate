# ADR-002: Database per service on one Postgres instance

Date: 2026-10-05 · Status: accepted

## Context

With several services (ADR-001), each one needs somewhere to store its data. If services share tables, they become coupled through the schema: one service's migration can break another, and the boundaries between services exist only on paper.

At the same time, the project runs on a laptop with little RAM and on a free server, so running a separate Postgres server for every service would waste memory for no real benefit at this scale.

## Decision

- **One PostgreSQL 17 instance**, with **one database and one owner role per service**: `users_db`, `catalog_db`, `reviews_db`.
- A service connects only to its own database, with its own credentials.
- The separation is **enforced, not just agreed on**:
  - `REVOKE CONNECT ... FROM PUBLIC` on each service database, because by default Postgres lets any role connect to any database.
  - Password authentication (`scram-sha-256`) for every TCP connection. The default image configuration trusted some local connections without a password, which made isolation tests pass for the wrong reason.
- Each service manages its own schema with Flyway migrations stored next to its code.
- No foreign keys across services. A review stores an `album_id` and a `user_id`, but the albums and users live in other databases.

## Alternatives considered

- **One shared database and schema.** Simplest, allows joins, but couples every service to every table. Rejected: it undoes the main reason for having separate services.
- **One database, one schema per service.** Lighter, but it is easy to read another service's schema by accident, and permissions are easier to get wrong. Rejected in favor of a harder boundary.
- **One Postgres instance per service.** The strongest isolation, but it multiplies RAM use and operational work for no gain at this scale. Can be adopted later without changing the services, since each already has its own connection settings.

## Consequences

Good:
- Each service can change its schema without coordinating with the others.
- A bug or a bad query in one service can't touch another service's data.
- Moving a service's database to its own server later only means changing its connection settings.

Bad:
- No joins across services. Data from another service comes through an API call or through events (for example, average ratings are kept in catalog-service, updated from review events).
- Data is duplicated in places and is only **eventually consistent**: after a review is saved, the album's average may take a moment to update.
- References like `album_id` in reviews aren't checked by the database. Review-service checks with catalog-service before saving instead.
- The single instance is a single point of failure for all services. Accepted for a friends app; backups are planned for the deployment phase.
