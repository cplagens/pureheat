#!/usr/bin/env bash
# PureHeat – copy the history DB of the debug build from the phone to /tmp/ph and print a short
# summary. Device: $ANDROID_SERIAL, or the only connected one. Query with scripts/sql/*.sql.
# The previous copy is only replaced when the new one is complete.
set -eu
if ! adb get-state > /dev/null 2>&1; then
  echo "no device connected (adb devices)" >&2
  exit 1
fi
mkdir -p /tmp/ph/new
rm -f /tmp/ph/new/*
adb exec-out run-as de.cplagens.pureheat cat databases/history.db > /tmp/ph/new/history.db
if ! sqlite3 /tmp/ph/new/history.db "SELECT 1 FROM room_sample LIMIT 1" > /dev/null 2>&1; then
  echo "copy failed (debug build installed?)" >&2
  exit 1
fi
for f in history.db-wal history.db-shm; do
  adb exec-out run-as de.cplagens.pureheat cat "databases/$f" > "/tmp/ph/new/$f" 2> /dev/null || true
  [ -s "/tmp/ph/new/$f" ] || rm -f "/tmp/ph/new/$f"
done
rm -f /tmp/ph/history.db /tmp/ph/history.db-wal /tmp/ph/history.db-shm
mv /tmp/ph/new/history.db* /tmp/ph/
sqlite3 /tmp/ph/history.db "SELECT 'rooms: ' || COUNT(DISTINCT roomId) || ', samples: ' || COUNT(*) ||
  ', last: ' || datetime(MAX(timestamp) / 1000, 'unixepoch', 'localtime') FROM room_sample"
