-- Sample pairs that would open a window, per room (id prefix) and day. Mirrors the opening rules of
-- WindowDetector (keep in sync): valve closed, step <= 75 min, absolute humidity falls >= 0.3 g/m³
-- (if known), and drop >= 0.3 K at >= 0.6 K/h since the value first appeared, or >= 0.6 g/m³ at
-- >= 0.2 K, or >= 0.5 K at >= 0.6 K/h between the two samples. Consecutive hits form one event.
WITH b AS (
  SELECT roomId, timestamp t, actualTemperature temp, valvePosition v, humidity h,
         6.112 * exp(17.67 * actualTemperature / (actualTemperature + 243.5)) * humidity * 2.1674 / (273.15 + actualTemperature) ah,
         CASE WHEN actualTemperature IS LAG(actualTemperature) OVER w THEN 0 ELSE 1 END chg
  FROM room_sample WINDOW w AS (PARTITION BY roomId ORDER BY timestamp)
), r AS (SELECT *, SUM(chg) OVER (PARTITION BY roomId ORDER BY t) run FROM b),
r2 AS (SELECT *, MIN(t) OVER (PARTITION BY roomId, run) since FROM r),
s AS (
  SELECT *, LAG(t) OVER w pt, LAG(temp) OVER w ptemp, LAG(v) OVER w pv, LAG(ah) OVER w pah, LAG(since) OVER w psince
  FROM r2 WINDOW w AS (PARTITION BY roomId ORDER BY t)
), c AS (
  SELECT *, ROUND(ptemp - temp, 2) d, pah - ah ahd,
         ROUND(ptemp - temp, 2) / ((t - psince) / 3600000.0) rs, ROUND(ptemp - temp, 2) / ((t - pt) / 3600000.0) rstep
  FROM s WHERE pt IS NOT NULL AND v <= 0.05 AND pv <= 0.05 AND t - pt <= 4500000
), k AS (
  SELECT *, (ahd IS NULL OR ahd >= 0.3) AND ((d >= 0.3 AND rs >= 0.6) OR (ahd >= 0.6 AND d >= 0.2) OR (d >= 0.5 AND rstep >= 0.6)) hit
  FROM c
)
SELECT substr(roomId, 1, 4) room, date(t / 1000, 'unixepoch', 'localtime') day, SUM(hit) hits,
       group_concat(CASE WHEN hit THEN strftime('%H:%M', t / 1000, 'unixepoch', 'localtime') END, ' ') times
FROM k GROUP BY room, day HAVING SUM(hit) > 0 ORDER BY room, day;
