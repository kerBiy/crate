-- Rating aggregates built from review.events (docs/SPEC.md sections 5.3 and 6).

-- How many ratings each half-star value got, for the album page's histogram.
-- Element i (1-based, like Postgres arrays) counts ratings of value i: 1 = ½ star … 10 = 5 stars.
alter table album_stats
  add column rating_distribution integer[] not null default '{0,0,0,0,0,0,0,0,0,0}'
    constraint album_stats_distribution_size check (cardinality(rating_distribution) = 10);

-- Events already applied. Inserted in the same transaction as the album_stats change, so a
-- redelivered event (at-least-once) is recognised and skipped.
create table processed_events (
  event_id     uuid primary key,
  processed_at timestamptz not null default now()
);
