#!/usr/bin/env bash
# Starts faux-person-web at http://127.0.0.1:8080/faux-person-web, until Ctrl-C.
# Builds the jar first when it's missing or older than the source, or when
# --rebuild is given. Use --rebuild after deleting a file: a plain build can
# leave the old copy in the jar.
#
#   ./run.sh                      build if needed, then start
#   ./run.sh --rebuild            clean build, then start
#   ./run.sh --server.port=9090   other arguments go to the app
#
# FAUX_WEB_JAVA_OPTS passes options to the JVM, e.g. FAUX_WEB_JAVA_OPTS=-Xmx1g
set -euo pipefail

# Follow a symlink to the script, so it finds the project wherever it's run from.
script="$0"
while [ -L "$script" ]; do
  target=$(readlink "$script")
  case "$target" in
    /*) script="$target" ;;
    *) script="$(dirname "$script")/$target" ;;
  esac
done
cd "$(dirname "$script")"
JAR=target/faux-person-web.jar

rebuild=false
args=()
for arg in "$@"; do
  if [ "$arg" = "--rebuild" ]; then
    rebuild=true
  else
    args+=("$arg")
  fi
done

if ! command -v java >/dev/null 2>&1; then
  echo "java not found. Install JDK 17 or later and put it on PATH." >&2
  exit 1
fi
# With no JDK, macOS's java stub fails here; || true lets the check below say so.
# "22.0.1" -> 22, and the old style "1.8.0_391" -> 8.
version=$(java -version 2>&1 | sed -n 's/.*version "\([^"]*\)".*/\1/p' | head -n 1) || true
major=${version%%.*}
[ "$major" = "1" ] && major=$(echo "$version" | cut -d. -f2)
if ! [ "$major" -ge 17 ] 2>/dev/null; then
  echo "java on PATH is version ${version:-unknown}; faux-person-web needs 17 or later." >&2
  exit 1
fi

stale=false
if [ ! -f "$JAR" ]; then
  stale=true
elif [ -n "$(find pom.xml src/main -newer "$JAR" -print -quit)" ]; then
  stale=true
fi

if $rebuild || $stale; then
  if ! command -v mvn >/dev/null 2>&1; then
    echo "mvn not found. Install Maven 3.6.3 or later to build the app." >&2
    exit 1
  fi
  goals=(package)
  $rebuild && goals=(clean package)
  echo "Building $JAR ..."
  # The tests run in mvn verify; starting the app shouldn't wait on them.
  mvn -q -B -Dmaven.test.skip=true "${goals[@]}"
fi

# FAUX_WEB_JAVA_OPTS splits on spaces, with no glob expansion.
set -f
# shellcheck disable=SC2086  # FAUX_WEB_JAVA_OPTS may hold several options.
exec java ${FAUX_WEB_JAVA_OPTS:-} -jar "$JAR" ${args[@]+"${args[@]}"}
