# ADR-006: Publish review events after the DB commit (known dual write) → outbox in Phase 2

Date: 2026-10-08 · Status: accepted (to be superseded by the Phase 2 outbox ADR)

## Context

When someone rates, changes or removes a rating, review-service changes a row in `reviews_db`, and
catalog-service must learn about it to keep `album_stats` (average, count, histogram) right. That
means **two writes to two systems**: Postgres and Kafka (`review.events`). No transaction spans
both, so one can succeed while the other fails. This is the *dual-write problem*:

- **Publish, then commit:** the commit can still fail (constraint, version conflict, crash). Catalog
  would then count a rating that doesn't exist.
- **Commit, then publish:** the process can die, or Kafka can be unreachable, right after the
  commit. The rating exists but catalog never hears about it.

There's no order of the two calls that is always right. Only a design where the event is written
in the *same* transaction as the data (an outbox) is.

## Decision

For the MVP, **publish after commit**:

- `ReviewService` publishes a Spring application event (`ReviewCreated` / `ReviewUpdated` /
  `ReviewDeleted`) inside its transaction.
- `ReviewEventPublisher` handles it with `@TransactionalEventListener(phase = AFTER_COMMIT)`: Spring
  calls it only once the transaction has committed, and drops the event on rollback (including a
  failed attempt in the upsert retry loop).
- It sends to `review.events` with key = `albumId`, asynchronously. The producer uses Kafka's
  defaults `acks=all` and idempotence, and `max.block.ms=3000` so a dead broker can't hang the
  HTTP request for 60 s.
- On failure it logs an **ERROR** naming the event id and album. That log line is the only record
  of a lost event.

**The failure window:** between the commit and the broker's acknowledgement. A crash, a restart, a
broker outage longer than the producer's retries (`delivery.timeout.ms`, 2 min), or metadata not
available within 3 s → the event is lost and that album's stats stay off by one rating until fixed
by hand.

Why that's acceptable now: it's a friends app, losses need a crash or an outage at the wrong
moment, the ERROR makes them visible, and the cost is a slightly wrong average, not lost user
data (the review itself is saved). It's listed in SPEC 3.6.

## Alternatives considered

- **Transactional outbox now** (SPEC 6.5): write the event to an `outbox` table in the same
  transaction; a relay publishes it and marks it sent. Fixes the problem (still at-least-once, which
  the idempotent consumer handles). Deferred to Phase 2 on purpose: it's more moving parts (relay,
  `FOR UPDATE SKIP LOCKED`, cleanup), and the plan is to learn it as its own step.
- **Debezium / CDC** reading the Postgres WAL into Kafka: no relay code, but another always-on
  service (Kafka Connect) and a lot more RAM than the €2/month VM has to spare. Recorded for the
  Phase 2 ADR.
- **Publish before commit:** announces changes that may roll back. Worse: catalog would count
  ratings that never existed.
- **Kafka transactions:** they make writes to several *Kafka* partitions atomic, but they can't
  include a Postgres commit. Doesn't solve this.
- **Wait for the broker ack inside the request:** the review is already committed, so a failure
  still can't be undone; it would only make every rating slower.

## Consequences

- Good: little code, easy to read and test; rolled-back changes are never announced (tested).
- Good: consumers are idempotent anyway (`processed_events`), so moving to the outbox later only
  changes the producer side.
- Bad: events can be lost after commit; `album_stats` can drift and nothing repairs it
  automatically.
- Damage control: catalog clamps a half-star bucket at 0 instead of storing a negative number, and
  logs a WARN with the event id (count and sum are derived from the buckets). A lost
  `ReviewCreated` followed by its `ReviewDeleted` then leaves the stats right, not at −1.
- Bad: the frontend's "settle on the server's numbers" step can't tell a lost event from a slow
  one; after ~8 s it shows the server's numbers, which then don't include the change.
- Ratings stored before this change produced no events. The local ones (4 dev reviews) were
  deleted on 2026-10-08 rather than backfilled.
