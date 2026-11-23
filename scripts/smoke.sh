#!/usr/bin/env bash
# Smoke-tests a running copy: the happy path and the error cases.
#   scripts/smoke.sh https://apps.tauasa.org/faux-person-web
#   scripts/smoke.sh http://127.0.0.1:8080/faux-person-web
set -u
base="${1:?usage: scripts/smoke.sh <base-url>}"
base="${base%/}"
fail=0
body=$(mktemp)
headers=$(mktemp)
trap 'rm -f "$body" "$headers"' EXIT

check() { # check <path> <wanted status>
  code=$(curl -s -o "$body" -D "$headers" -w '%{http_code}' "$base$1")
  if [ "$code" = "$2" ]; then echo "ok   $1 -> $code"; else echo "FAIL $1 -> $code, wanted $2"; fail=1; fi
}

check /health 200
grep -qx 'ok' "$body" || { echo "FAIL /health did not say ok"; fail=1; }

check "/people?count=25&seed=42&format=csv" 200
[ "$(tr -d '\r' < "$body" | wc -l | tr -d ' ')" = 26 ] || { echo "FAIL the CSV did not hold a header and 25 rows"; fail=1; }
grep -qi '^x-faux-seed: 42' "$headers" || { echo "FAIL no X-Faux-Seed: 42 header"; fail=1; }
first=$(cat "$body")
check "/people?count=25&seed=42&format=csv" 200
[ "$(cat "$body")" = "$first" ] || { echo "FAIL the same seed gave different rows"; fail=1; }

check "/names?count=3&seed=1&format=json" 200
check "/companies?count=10&seed=1" 200
grep -q 'id="shown-seed">1<' "$body" || { echo "FAIL the page did not show its seed"; fail=1; }

check "/people?count=0&format=csv" 400
check /no-such-page 404

exit $fail
