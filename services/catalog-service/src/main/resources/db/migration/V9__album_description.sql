-- The album page's description: the intro of its English Wikipedia article (docs/adr/014-wikipedia-descriptions.md),
-- fetched the first time someone opens the album and fetched again after 30 days.
create table album_descriptions (
  album_id      uuid primary key references albums on delete cascade,
  text          text,                     -- the article's first paragraph, plain text; null: no English article
  article_url   text,                     -- the article, for the "From Wikipedia" credit (CC BY-SA)
  article_title text,
  fetched_at    timestamptz not null
);
