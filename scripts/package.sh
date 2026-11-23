#!/usr/bin/env bash
# Builds the release package the deploy uploads: the jar and deploy/, as
# target/faux-person-web-<name>.tar.gz. Run after `mvn package`.
#   scripts/package.sh <name>     e.g. the commit's short SHA
set -euo pipefail
name="${1:?usage: scripts/package.sh <name>}"
case "$name" in
  *[!A-Za-z0-9._-]*) echo "package name may hold only letters, digits, . _ -" >&2; exit 2 ;;
esac
root="$(cd "$(dirname "$0")/.." && pwd)"
jar="$root/target/faux-person-web.jar"
[ -f "$jar" ] || { echo "no $jar: run mvn package first" >&2; exit 1; }

out="$root/target/faux-person-web-$name.tar.gz"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT
cp "$jar" "$stage/faux-person-web.jar"
cp -R "$root/deploy" "$stage/deploy"
tar -C "$stage" -czf "$out" faux-person-web.jar deploy
echo "$out"
