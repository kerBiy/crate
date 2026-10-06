#!/usr/bin/env bash
# Records the real MusicBrainz responses used as WireMock fixtures by MusicBrainzClientTest.
# Run once (or when MusicBrainz's JSON changes), never from tests:
#   MUSICBRAINZ_CONTACT=you@example.com ./record-musicbrainz-fixtures.sh
# Queries are written exactly as MusicBrainzQueryBuilder builds them.
set -euo pipefail

: "${MUSICBRAINZ_CONTACT:?set MUSICBRAINZ_CONTACT to your contact email}"
USER_AGENT="Crate/0.1 ( ${MUSICBRAINZ_CONTACT} )"
BASE=https://musicbrainz.org/ws/2
OUT="$(dirname "$0")/__files/musicbrainz"
mkdir -p "$OUT"

fetch() { # <file> <path> [curl args...]
  local file=$1 path=$2; shift 2
  curl -sSf -G -A "$USER_AGENT" -H 'Accept: application/json' \
    --data-urlencode fmt=json "$@" "$BASE/$path" | python3 -m json.tool --no-ensure-ascii > "$OUT/$file"
  echo "recorded $file"
  sleep 2 # MusicBrainz allows ~1 request/second; 1.1 s still got a 503 while recording
}

fetch search-radiohead-ok-computer.json release-group --data-urlencode limit=5 \
  --data-urlencode 'query=artist:(Radiohead) AND releasegroup:(OK Computer) AND primarytype:(album OR ep)'
# No type clause on purpose: mixes Albums with Singles/Other so the client-side filter has work to do.
fetch search-radiohead-all-types.json release-group --data-urlencode limit=10 \
  --data-urlencode 'query=artist:(Radiohead) AND releasegroup:(Creep OR Airbag OR "OK Computer")'
fetch search-jay-z-watch-the-throne.json release-group --data-urlencode limit=3 \
  --data-urlencode 'query=artist:(JAY\-Z) AND releasegroup:(Watch the Throne) AND primarytype:(album OR ep)'
fetch lookup-ok-computer.json release-group/b1392450-e666-3926-a536-22c65f834433 \
  --data-urlencode inc=artist-credits
fetch lookup-creep-single.json release-group/c5bc370b-95c2-3634-bb89-51bb2dce97c3 \
  --data-urlencode inc=artist-credits
