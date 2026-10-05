-- Accounts (docs/SPEC.md section 5.2). The follows table arrives with the follow feature.
create table users (
  id            uuid primary key,
  username      varchar(30)  not null unique,   -- lowercase, [a-z0-9_]
  email         varchar(254) not null unique,   -- stored lowercase
  password_hash varchar(100) not null,          -- BCrypt
  display_name  varchar(60),
  created_at    timestamptz  not null default now()
);
