-- Samples of one room for the last :hours hours (set :room and :hours with -cmd ".param set …").
SELECT time(timestamp / 1000, 'unixepoch', 'localtime') t, actualTemperature temp, setPointTemperature sp,
       valvePosition v, humidity h,
       ROUND(6.112 * exp(17.67 * actualTemperature / (actualTemperature + 243.5)) * humidity * 2.1674 / (273.15 + actualTemperature), 2) ah
FROM room_sample
WHERE roomId = :room AND timestamp > (strftime('%s', 'now') - :hours * 3600) * 1000
ORDER BY timestamp;
