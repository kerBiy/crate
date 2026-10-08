# ADR-007: Feed v1 is fan-out on read → fan-out on write in Phase 2

Date: 2026-10-08 · Status: accepted (to be superseded by the Phase 2 activity-service ADR)

## Context

The friends feed is "the reviews of everyone I follow, newest first". The data for it lives in two
services: the follow graph in user-service (`users_db.follows`), the reviews in review-service
(`reviews_db.reviews`). Database per service (ADR-002) rules out a join across them.

There are two classic ways to build a feed:

- **Fan-out on read:** nothing extra happens when someone rates. When I open my feed, look up who I
  follow and query their reviews: `where user_id = any(:ids) order by created_at desc, id desc`.
- **Fan-out on write:** when someone rates, write one feed entry per follower into a precomputed
  per-user feed. Reading is then one indexed lookup by owner.

## Decision

For Phase 1, **fan-out on read**, in review-service (SPEC 5.4):

1. `GET /reviews/feed` asks user-service `GET /users/{me}/following/ids` (2 s timeout, SPEC 3.3).
   The call is made before any DB work, so no connection is held while waiting.
2. If user-service fails, times out or can't be reached: **503** `user-service-unavailable`. Never
   an empty feed: "you follow nobody" and "we couldn't ask" must look different.
3. Following nobody: an empty page, without touching the DB.
4. Otherwise one keyset-paginated query (SPEC 7) over `reviews`, with the ids bound as a `uuid[]`.
5. The client renders a page with two batch calls (`GET /users?ids=`, `GET /albums?ids=`), not one
   call per row (no N+1).

## Alternatives considered

- **Fan-out on write now:** needs the follow graph inside the service that owns the feed, i.e. a
  replica built from follow events (SPEC 5.5), plus an outbox so no event is lost. That's Phase 2
  work; doing it now would pull most of Phase 2 into Phase 1.
- **review-service stores its own copy of follows:** the same replica problem, in the wrong service.
- **The frontend fetches following ids and passes them to review-service:** two round trips from the
  browser, and a URL that grows with the follow count.

## Consequences

Good:
- No new tables, events or services. Writes stay as cheap as they are.
- Always consistent: a new follow shows up in the feed immediately.

Bad:
- Every feed page costs a sync call to user-service, so user-service down means no feed (503).
  No circuit breaker yet (Resilience4j comes in Phase 2).
- The query reads reviews from every followed user and sorts them. It gets slower as follows and
  reviews grow, and the id list travels on every request. Fine at friend scale; measured against
  fan-out on write with k6 in Phase 3.
- `GET /users/{id}/following/ids` is reachable through the gateway too. It reveals nothing that
  `/users/{id}/following` doesn't.

Phase 2 replaces this with `activity-service` (fan-out on write from `review.events` and the compacted
`follow.state` topic). The usual problem there is the "celebrity" user with huge follower counts,
whose every rating means one write per follower; the usual fix is a hybrid (fan-out on read for those
users). Not needed at friend scale, but it belongs in that ADR.
