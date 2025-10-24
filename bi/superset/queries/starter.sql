-- 事件趋势
SELECT project_id, event_date, event_name, countMerge(evts) AS events
FROM mv_events_by_day
GROUP BY project_id, event_date, event_name
ORDER BY project_id, event_date;

-- DAU
SELECT project_id, event_date, uniqExactMerge(dau) AS dau
FROM mv_dau
GROUP BY project_id, event_date
ORDER BY project_id, event_date;
