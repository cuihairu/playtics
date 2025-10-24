-- UA / OS / Device 维度分析示例

-- 1) 按浏览器族分布（近7天）
SELECT JSONExtractString(props_json,'ua_family') AS ua_family, count() AS c
FROM events
WHERE ts_server >= now() - INTERVAL 7 DAY
GROUP BY ua_family
ORDER BY c DESC
LIMIT 20;

-- 2) 按操作系统族分布（近7天）
SELECT JSONExtractString(props_json,'os_family') AS os_family, count() AS c
FROM events
WHERE ts_server >= now() - INTERVAL 7 DAY
GROUP BY os_family
ORDER BY c DESC
LIMIT 20;

-- 3) 按设备类型（Mobile/Desktop/Tablet/TV 等）分布
SELECT JSONExtractString(props_json,'device_class') AS device_class, count() AS c
FROM events
GROUP BY device_class
ORDER BY c DESC;

-- 4) 事件趋势（按浏览器族）
SELECT event_date, JSONExtractString(props_json,'ua_family') AS ua_family, count() AS events
FROM events
GROUP BY event_date, ua_family
ORDER BY event_date;
