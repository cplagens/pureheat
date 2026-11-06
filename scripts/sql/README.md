# SQL for the history DB

Run after `scripts/pull-db.sh`. Only aggregates or single rooms – never dump whole tables (the output
goes into the agent's context, and room ids must not end up in the repo).

```
sqlite3 -header -column /tmp/ph/history.db < scripts/sql/sampling.sql
sqlite3 -header -column /tmp/ph/history.db < scripts/sql/window-hits.sql
sqlite3 -header -column -cmd ".param set :room '<roomId>'" -cmd ".param set :hours 6" \
  /tmp/ph/history.db < scripts/sql/room-samples.sql
```

| File | Shows |
|---|---|
| `sampling.sql` | per day: samples, average interval, share with valve closed; size of temperature steps |
| `window-hits.sql` | per room (id prefix) and day: sample pairs that would open a window (same rules as `WindowDetector`) |
| `room-samples.sql` | the samples of one room for the last `:hours` hours |

Room names: `files/last_state.json` → `jq -r 'paths(type == "string" and test("<label>")) | map(tostring) | join(".")'`
gives `groups.<id>.label` (only print paths, never the file).
