#!/usr/bin/env bash
# Records the real MusicBrainz (and ListenBrainz) responses used as WireMock fixtures.
# Run once (or when their JSON changes), never from tests:
#   MUSICBRAINZ_CONTACT=you@example.com ./record-musicbrainz-fixtures.sh
# Pass file names to record only those, e.g. ./record-musicbrainz-fixtures.sh release-ok-computer.json
# Queries are written exactly as MusicBrainzQueryBuilder builds them (the first three predate
# ADR 010; the client tests only use them for JSON mapping).
set -euo pipefail

: "${MUSICBRAINZ_CONTACT:?set MUSICBRAINZ_CONTACT to your contact email}"
USER_AGENT="Crate/0.1 ( ${MUSICBRAINZ_CONTACT} )"
BASE=https://musicbrainz.org/ws/2
OUT="$(dirname "$0")/__files/musicbrainz"
mkdir -p "$OUT"

wanted() { # <file>: true when no names were given, or this is one of them
  [[ ${#ONLY[@]} -eq 0 ]] && return 0
  local name; for name in "${ONLY[@]}"; do [[ $name == "$1" ]] && return 0; done
  return 1
}
ONLY=("$@")

fetch() { # <file> <path> [curl args...]
  local file=$1 path=$2; shift 2
  wanted "$file" || return 0
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

# ADR 010 queries: every word required, excluded secondary types unless the title is the phrase.
EXCLUDED='secondarytype:("compilation" OR "live" OR "remix" OR "dj-mix" OR "mixtape/street" OR "demo" OR "interview" OR "audiobook" OR "audio drama")'
RADIOHEAD='((releasegroup:(radiohead) OR artist:(radiohead)))'
fetch search-radiohead.json release-group --data-urlencode limit=10 \
  --data-urlencode "query=($RADIOHEAD AND primarytype:(album OR ep) AND NOT $EXCLUDED) OR ($RADIOHEAD AND primarytype:(album OR ep) AND releasegroup:\"radiohead\")"
FOLSOM='((releasegroup:(at) OR artist:(at)) AND (releasegroup:(folsom) OR artist:(folsom)) AND (releasegroup:(prison) OR artist:(prison)))'
fetch search-at-folsom-prison.json release-group --data-urlencode limit=5 \
  --data-urlencode "query=($FOLSOM AND primarytype:(album OR ep) AND NOT $EXCLUDED) OR ($FOLSOM AND primarytype:(album OR ep) AND releasegroup:\"at folsom prison\")"
fetch artist-albums-radiohead.json release-group --data-urlencode limit=10 \
  --data-urlencode "query=arid:a74b1b7f-71a5-4011-9441-d0b5e4122711 AND primarytype:album AND NOT $EXCLUDED"

fetch lookup-ok-computer.json release-group/b1392450-e666-3926-a536-22c65f834433 \
  --data-urlencode inc=artist-credits
fetch lookup-creep-single.json release-group/c5bc370b-95c2-3634-bb89-51bb2dce97c3 \
  --data-urlencode inc=artist-credits

# ADR 013: the album page's release. Browses come back in no particular order; the rule sorts them.
OK_COMPUTER=b1392450-e666-3926-a536-22c65f834433
DISCOVERY=48117b90-a16e-34ca-a514-19c702df1158
MELLON_COLLIE=1af599c9-0b44-3a5a-a06b-39e8db6b2b4e
fetch browse-releases-ok-computer.json release --data-urlencode release-group=$OK_COMPUTER \
  --data-urlencode status=official --data-urlencode inc=labels+media --data-urlencode limit=100
fetch browse-releases-discovery.json release --data-urlencode release-group=$DISCOVERY \
  --data-urlencode status=official --data-urlencode inc=labels+media --data-urlencode limit=100
fetch browse-releases-mellon-collie.json release --data-urlencode release-group=$MELLON_COLLIE \
  --data-urlencode status=official --data-urlencode inc=labels+media --data-urlencode limit=100
# The releases ReleaseChoice picks from those browses: XE Parlophone CD, GB Virgin 2 CDs.
fetch release-ok-computer.json release/425d8db9-88a3-31e2-bd23-b1a967626dd5 \
  --data-urlencode inc=recordings+labels+release-groups+genres
fetch release-mellon-collie.json release/15cf95c2-bbe6-37cc-8a61-c5d5116b267a \
  --data-urlencode inc=recordings+labels+release-groups+genres

# ListenBrainz popularity (ADR 012): OK Computer, and an MBID ListenBrainz doesn't know.
LB_OUT="$(dirname "$0")/__files/listenbrainz"
mkdir -p "$LB_OUT"
if wanted popularity-ok-computer.json; then
  curl -sSf -A "$USER_AGENT" -H 'Content-Type: application/json' \
    --data "{\"release_group_mbids\": [\"$OK_COMPUTER\", \"00000000-0000-0000-0000-000000000001\"]}" \
    https://api.listenbrainz.org/1/popularity/release-group | python3 -m json.tool --no-ensure-ascii > "$LB_OUT/popularity-ok-computer.json"
  echo "recorded popularity-ok-computer.json"
fi
