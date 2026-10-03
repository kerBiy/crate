#!/bin/sh
# Runs once, on the first start with an empty data volume.
# Creates one database + one owner role per service (SPEC section 3.4).
# Each role can connect only to its own database.
set -eu

create_service_db() {
  db="$1"
  role="$2"
  password="$3"

  # Password passed as a psql variable and quoted with :'pw', never pasted into the SQL text
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
    -v pw="$password" <<-EOSQL
	CREATE ROLE $role LOGIN PASSWORD :'pw';
	CREATE DATABASE $db OWNER $role;
	-- Postgres grants CONNECT and TEMPORARY to PUBLIC by default: take them away
	REVOKE ALL ON DATABASE $db FROM PUBLIC;
	GRANT CONNECT, TEMPORARY ON DATABASE $db TO $role;
EOSQL
}

create_service_db users_db   user_service    "$USERS_DB_PASSWORD"
create_service_db catalog_db catalog_service "$CATALOG_DB_PASSWORD"
create_service_db reviews_db review_service  "$REVIEWS_DB_PASSWORD"

# Service roles have no business in the maintenance database either
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres \
  -c "REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;"
