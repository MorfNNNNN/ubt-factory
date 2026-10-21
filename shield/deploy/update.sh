#!/bin/sh
# Pull-based updates for a server set up by install.sh, which installs this script as
# /usr/local/sbin/family-shield-update and runs it from cron every 10 minutes. GitHub Actions
# cannot push over SSH (SSH to the server from abroad is filtered), so the server checks
# GitHub itself and re-runs the installer when the deployed branch or the CI-built server
# jar has changed. `--record` only remembers the current state.
set -eu
. /etc/family-shield.conf

exec 9>/run/family-shield-update.lock
flock -n 9 || exit 0

state_file=/var/lib/family-shield/update-state
head=$(git -C "$DIR" ls-remote origin "refs/heads/$BRANCH" | cut -f1)
[ -n "$head" ] || exit 0
# Release assets refuse HEAD requests; a one-byte ranged GET returns the same ETag.
jar=$(curl -fsSL -r 0-0 -D - -o /dev/null "$JAR_URL" 2>/dev/null | tr -d '\r' |
  awk 'tolower($1) == "etag:" { e = $2 } END { print e }')
state="$head $jar"
mkdir -p "$(dirname "$state_file")"

if [ "${1:-}" = "--record" ]; then
  echo "$state" >"$state_file"
  exit 0
fi
[ "$state" != "$(cat "$state_file" 2>/dev/null)" ] || exit 0

echo "$(date -Is) updating: $state"
git -C "$DIR" fetch -q --depth 1 origin "$BRANCH"
git -C "$DIR" reset -q --hard FETCH_HEAD
SHIELD_BRANCH="$BRANCH" SHIELD_DIR="$DIR" SHIELD_JAR_URL="$JAR_URL" \
  bash "$DIR/shield/deploy/install.sh" "$DOMAIN" </dev/null
echo "$state" >"$state_file"
echo "$(date -Is) done"
