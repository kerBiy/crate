-- Whether the Cover Art Archive has a front cover for the album (SPEC 5.3).
-- has_cover: null = not checked yet (or the archive didn't answer). Search hides only false.
-- cover_checked_at: when has_cover was last set; "no cover" is asked again after 30 days.
alter table albums
  add column has_cover        boolean,
  add column cover_checked_at timestamptz;

-- Cached rankings were made before covers were checked and would keep coverless albums for up to
-- 7 days: ask MusicBrainz again (only a cache; search_results go with it via the foreign key).
delete from search_cache;
