-- Daily pre-aggregations (no dimensions) for experiments

-- 1) Daily exposures by variant
CREATE TABLE IF NOT EXISTS exp_daily_exposures
(
  project_id String,
  event_date Date,
  exp String,
  variant String,
  exposures UInt64,
  users UInt64
)
ENGINE = SummingMergeTree
PARTITION BY (project_id, toYYYYMM(event_date))
ORDER BY (project_id, event_date, exp, variant)
TTL event_date + INTERVAL 400 DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_exp_daily_exposures
TO exp_daily_exposures AS
SELECT project_id,
       toDate(expose_ts) AS event_date,
       exp,
       variant,
       count() AS exposures,
       uniqExact(uid) AS users
FROM exp_exposure_users
GROUP BY project_id, event_date, exp, variant;

-- 2) Daily 24h conversion by variant
CREATE TABLE IF NOT EXISTS exp_daily_conv_24h
(
  project_id String,
  exposure_date Date,
  exp String,
  variant String,
  exposed_users UInt64,
  converted_users UInt64,
  cr_24h Float32
)
ENGINE = ReplacingMergeTree
PARTITION BY (project_id, toYYYYMM(exposure_date))
ORDER BY (project_id, exposure_date, exp, variant)
TTL exposure_date + INTERVAL 400 DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_exp_daily_conv_24h
TO exp_daily_conv_24h AS
WITH conv AS (
  SELECT project_id, uid, min(conv_ts) AS conv_ts
  FROM exp_first_level_complete
  GROUP BY project_id, uid
)
SELECT e.project_id,
       toDate(e.expose_ts) AS exposure_date,
       e.exp,
       e.variant,
       count() AS exposed_users,
       countIf(c.conv_ts >= e.expose_ts AND c.conv_ts <= e.expose_ts + INTERVAL 24 HOUR) AS converted_users,
       round(converted_users / nullIf(exposed_users,0), 4) AS cr_24h
FROM exp_exposure_users e
LEFT JOIN conv c ON c.project_id=e.project_id AND c.uid=e.uid
GROUP BY e.project_id, exposure_date, e.exp, e.variant;

-- 3) Daily 7d conversion by variant
CREATE TABLE IF NOT EXISTS exp_daily_conv_7d
(
  project_id String,
  exposure_date Date,
  exp String,
  variant String,
  exposed_users UInt64,
  converted_users UInt64,
  cr_7d Float32
)
ENGINE = ReplacingMergeTree
PARTITION BY (project_id, toYYYYMM(exposure_date))
ORDER BY (project_id, exposure_date, exp, variant)
TTL exposure_date + INTERVAL 400 DAY DELETE;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_exp_daily_conv_7d
TO exp_daily_conv_7d AS
WITH conv AS (
  SELECT project_id, uid, min(conv_ts) AS conv_ts
  FROM exp_first_level_complete
  GROUP BY project_id, uid
)
SELECT e.project_id,
       toDate(e.expose_ts) AS exposure_date,
       e.exp,
       e.variant,
       count() AS exposed_users,
       countIf(c.conv_ts >= e.expose_ts AND c.conv_ts <= e.expose_ts + INTERVAL 7 DAY) AS converted_users,
       round(converted_users / nullIf(exposed_users,0), 4) AS cr_7d
FROM exp_exposure_users e
LEFT JOIN conv c ON c.project_id=e.project_id AND c.uid=e.uid
GROUP BY e.project_id, exposure_date, e.exp, e.variant;
