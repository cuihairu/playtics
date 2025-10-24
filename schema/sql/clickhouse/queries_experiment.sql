-- 实验曝光（每日、按 variant）
CREATE OR REPLACE VIEW v_exp_exposures_by_day AS
SELECT project_id, toDate(ts_server) AS event_date,
       JSONExtractString(props_json,'exp') AS exp,
       JSONExtractString(props_json,'variant') AS variant,
       count() AS exposures,
       uniqExact(if(user_id!='',user_id,device_id)) AS users
FROM events
WHERE event_name = 'experiment_exposure'
GROUP BY project_id, event_date, exp, variant;

-- 示例：转化（以 level_complete 为 primary），按曝光当日 + 同日转化率（演示用）
CREATE OR REPLACE VIEW v_exp_conversion_same_day AS
WITH exp_users AS (
  SELECT project_id, toDate(ts_server) AS d, JSONExtractString(props_json,'exp') AS exp,
         JSONExtractString(props_json,'variant') AS variant,
         groupUniqArray(if(user_id!='',user_id,device_id)) AS users
  FROM events
  WHERE event_name='experiment_exposure'
  GROUP BY project_id, d, exp, variant
),
conversions AS (
  SELECT project_id, toDate(ts_server) AS d, if(user_id!='',user_id,device_id) AS uid,
         count() AS cnt
  FROM events
  WHERE event_name='level_complete'
  GROUP BY project_id, d, uid
)
SELECT e.project_id, e.d AS event_date, e.exp, e.variant,
       arraySize(e.users) AS exposed_users,
       countIf(c.uid IN e.users) AS converted_users,
       round(converted_users / nullIf(exposed_users,0), 4) AS cr
FROM exp_users e
LEFT JOIN conversions c ON c.project_id=e.project_id AND c.d=e.d
GROUP BY e.project_id, e.d, e.exp, e.variant;
