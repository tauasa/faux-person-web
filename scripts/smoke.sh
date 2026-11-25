#!/usr/bin/env bash
# Smoke-tests a running copy: the happy path, the page's own files and the error cases.
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

check "/people?count=25&seed=42&format=csv&zip=true" 200
grep -qi '^content-type: *application/zip' "$headers" || { echo "FAIL the zip was not application/zip"; fail=1; }
[ "$(unzip -p "$body")" = "$first" ] || { echo "FAIL the zip did not hold the same CSV"; fail=1; }

check "/names?count=3&seed=1&format=json" 200
check "/companies?count=10&seed=1" 200
grep -q 'id="shown-seed">1<' "$body" || { echo "FAIL the page did not show its seed"; fail=1; }

# The page's own files, at the addresses the page gives them. Each address
# must be under this copy's path, and its v must be the hash of what's served.
path=$(printf '%s' "$base" | sed -E 's#^https?://[^/]+##')
page=$(cat "$body")
asset() { # asset <file> <wanted content type>
  link=$(printf '%s' "$page" | grep -oE "(href|src)=\"[^\"]*/$1(\?[^\"]*)?\"" | head -n 1 | cut -d'"' -f2)
  case "$link" in
    "$path/$1?v="?*) ;;
    *) echo "FAIL the page links $1 as '$link', wanted $path/$1?v=<hash>"; fail=1; return ;;
  esac
  check "${link#"$path"}" 200
  grep -qiE "^content-type: *$2" "$headers" || { echo "FAIL $1 was not $2"; fail=1; }
  [ "$(shasum -a 256 < "$body" | cut -c1-16)" = "${link##*=}" ] || { echo "FAIL $1 is not the file its address names"; fail=1; }
}
asset app.css text/css
asset app.js text/javascript

check "/people?count=0&format=csv" 400
check /no-such-page 404

exit $fail
