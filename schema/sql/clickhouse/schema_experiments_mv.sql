-- Experiment MVs and helper tables for performance

-- 1) Exposure users (one row per exposure event with user + dims)
CREATE TABLE IF NOT EXISTS exp_exposure_users
(
  project_id String,
  exp String,
  variant String,
  uid String,
  expose_ts DateTime64(3),
  platform LowCardinality(String),
  app_version LowCardinality(String),
  country FixedString(2)
)
ENGINE = MergeTree
PARTITION BY (project_id, toYYYYMM(expose_ts))
ORDER BY (project_id, exp, variant, uid, expose_ts);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_exp_exposure_users
TO exp_exposure_users AS
SELECT project_id,
       JSONExtractString(props_json,'exp') AS exp,
       JSONExtractString(props_json,'variant') AS variant,
       if(user_id!='',user_id,device_id) AS uid,
       ts_server AS expose_ts,
       platform, app_version, country
FROM events
WHERE event_name='experiment_exposure';

-- 2) First conversion per user (primary metric assumed 'level_complete' for MVP)
CREATE TABLE IF NOT EXISTS exp_first_level_complete
(
  project_id String,
  uid String,
  conv_ts DateTime64(3)
)
ENGINE = ReplacingMergeTree
ORDER BY (project_id, uid);

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_exp_first_level_complete
TO exp_first_level_complete AS
SELECT project_id,
       if(user_id!='',user_id,device_id) AS uid,
       min(ts_server) AS conv_ts
FROM events
WHERE event_name='level_complete'
GROUP BY project_id, uid;
