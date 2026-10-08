-- The follow graph (docs/SPEC.md section 5.2). Asymmetric, like Letterboxd: "friends" = people you follow.
create table follows (
  follower_id uuid not null references users(id) on delete cascade,
  followee_id uuid not null references users(id) on delete cascade,
  created_at  timestamptz not null default now(),
  primary key (follower_id, followee_id),
  -- The API answers 400 first; this is the guarantee.
  check (follower_id <> followee_id)
);

-- Keyset pagination of the two lists, newest follow first (SPEC section 7).
-- "Who follows X" also serves follower counts; the primary key serves "who X follows" lookups.
create index follows_followee_idx on follows (followee_id, created_at desc, follower_id desc);
create index follows_follower_idx on follows (follower_id, created_at desc, followee_id desc);
