#!/usr/bin/env bash
# PureHeat – build, unit tests and lint with a short summary. Full log: /tmp/ph/build.log.
# Usage: scripts/check.sh [extra Gradle tasks or options]
set -u
cd "$(dirname "$0")/.."
mkdir -p /tmp/ph
./gradlew assembleDebug testDebugUnitTest lintDebug "$@" --continue --console=plain > /tmp/ph/build.log 2>&1
status=$?

grep -E "^e: |BUILD (SUCCESSFUL|FAILED)" /tmp/ph/build.log | head -20

lint=app/build/reports/lint-results-debug.txt
if [ -f "$lint" ]; then
  echo "lint errors: $(grep -c ': Error:' "$lint")"
  grep ': Error:' "$lint" | head -10
fi

results=app/build/test-results/testDebugUnitTest
if ls "$results"/*.xml > /dev/null 2>&1; then
  grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' "$results"/*.xml |
    awk -F'"' '{t += $2; f += $6; e += $8} END {printf "tests: %d, failures: %d, errors: %d\n", t, f, e}'
  # Failed tests: class, name and the start of the message.
  awk '
    /<testcase / { match($0, /name="[^"]*"/); n = substr($0, RSTART + 6, RLENGTH - 7)
                   match($0, /classname="[^"]*"/); c = substr($0, RSTART + 11, RLENGTH - 12) }
    /<(failure|error) / { match($0, /message="[^"]*/); m = substr($0, RSTART + 9, 200)
                          sub(/.*\./, "", c); print "FAILED " c " > " n ": " m }
  ' "$results"/*.xml | head -10
fi
exit $status
