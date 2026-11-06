-- Per day: number of sample pairs, average interval, share with the valve closed.
WITH s AS (
  SELECT timestamp t, valvePosition v,
         LAG(timestamp) OVER w pt, LAG(valvePosition) OVER w pv
  FROM room_sample WINDOW w AS (PARTITION BY roomId ORDER BY timestamp)
)
SELECT date(t / 1000, 'unixepoch', 'localtime') day, COUNT(*) pairs,
       ROUND(AVG((t - pt) / 60000.0), 1) avg_step_min,
       ROUND(100.0 * SUM(v <= 0.05 AND pv <= 0.05) / COUNT(*)) pct_valve_closed
FROM s WHERE pt IS NOT NULL GROUP BY day ORDER BY day;

-- Size of the temperature steps between samples (coarse steps = sensor reports only bigger changes).
WITH s AS (
  SELECT actualTemperature temp, LAG(actualTemperature) OVER (PARTITION BY roomId ORDER BY timestamp) p
  FROM room_sample
)
SELECT ROUND(ABS(temp - p), 1) step_k, COUNT(*) n FROM s WHERE p IS NOT NULL AND temp != p GROUP BY step_k ORDER BY step_k;
