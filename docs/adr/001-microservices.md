# ADR-001: Microservices with four services

Date: 2026-10-05 · Status: accepted

## Context

Crate is a small app for a group of friends: rate and review albums, follow people, see what friends listen to. At this scale a single application would handle the load easily.

The project also has a second goal: to be a portfolio piece that demonstrates backend and distributed-systems skills, built alongside a Master's in Distributed Systems. The problems worth learning (service boundaries, communication over the network, events, failure handling, deployment of several services) only appear when there is more than one service.

## Decision

Split the backend into four services from the start, each owning its own data:

- `gateway`: single entry point, routing, JWT validation
- `user-service`: accounts, login, follow graph
- `catalog-service`: albums, search, MusicBrainz integration, rating aggregates
- `review-service`: ratings and reviews

A fifth service (`activity-service`, feeds and notifications) is planned for Phase 2. No other service is added without a new ADR.

The boundaries follow the data: each service owns one clear part of the domain, and none needs another's tables to do its main job.

## Alternatives considered

- **Monolith.** Simplest to build, test and deploy, and the right choice for this scale if learning weren't a goal. Rejected because it would hide exactly the problems the project is meant to explore.
- **Modular monolith** (one deployable, strict internal modules). Keeps boundaries without the network cost, and is what I would recommend for a real product of this size. Rejected for the same reason: no network calls, no independent deploys, no partial failures to handle.
- **More, smaller services** (e.g. separate search, auth, ratings, social). Rejected: more moving parts and RAM on a small server, without teaching anything new.

## Consequences

Good:
- Real experience with service boundaries, synchronous calls between services, events over Kafka, and per-service databases.
- Each service is small, can be understood on its own, and has its own tests and migrations.
- Clear story to discuss in interviews, including why this is more than the app strictly needs.

Bad:
- Much more infrastructure than the product needs: a gateway, several JVMs, Kafka, one database per service.
- Calls between services can fail or be slow, so timeouts, retries and fallbacks become necessary.
- Data that a monolith would get with a join now needs an API call or a copy kept up to date through events.
- Debugging a request means following it across services (planned: request IDs now, tracing in Phase 3).
- Higher memory use on both the laptop and the server.

These costs are accepted on purpose, because dealing with them is the point of the project.
