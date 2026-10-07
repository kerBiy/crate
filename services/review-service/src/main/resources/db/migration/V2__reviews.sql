-- One rating (and optional review text) per user per album. SPEC section 5.4.
create table reviews (
  id         uuid primary key,
  user_id    uuid not null,       -- no FK: users live in user-service
  album_id   uuid not null,       -- no FK: albums live in catalog-service
  rating     smallint not null check (rating between 1 and 10),  -- half stars: 1 = 0.5, 10 = 5
  body       text check (char_length(body) <= 5000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version    bigint not null default 0,   -- optimistic locking (@Version)
  -- Named, so ReviewService can tell "a concurrent create won" from any other violation.
  constraint reviews_user_album_key unique (user_id, album_id)
);

-- Keyset pagination: (created_at, id) is the cursor, newest first (SPEC section 7).
create index reviews_album_idx on reviews (album_id, created_at desc, id desc);
create index reviews_user_idx  on reviews (user_id,  created_at desc, id desc);
