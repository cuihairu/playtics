-- ClickHouse DDL（Playtics）

CREATE TABLE IF NOT EXISTS events
(
  project_id String,
  event_date Date DEFAULT toDate(ts_server),
  ts_server DateTime64(3) DEFAULT now64(3),
  event_id String,
  event_name LowCardinality(String),
  user_id String DEFAULT '',
  device_id String,
  session_id String DEFAULT '',
  platform LowCardinality(String),
  app_version LowCardinality(String),
  country FixedString(2) DEFAULT '' ,
  props_json JSON,
  revenue_amount Decimal(18,4) DEFAULT 0,
  revenue_currency FixedString(3) DEFAULT 'USD'
)
ENGINE = MergeTree
PARTITION BY (project_id, toYYYYMM(event_date))
ORDER BY (project_id, user_id, device_id, ts_server, event_name, event_id)
TTL event_date + INTERVAL 365 DAY;

CREATE MATERIALIZED VIEW IF NOT EXISTS mv_events_by_day
ENGINE = AggregatingMergeTree
PARTITION BY (project_id, toYYYYMM(event_date))
ORDER BY (project_id, event_date, event_name)
AS
SELECT project_id, event_date, event_name,
       countState() AS evts
FROM events
GROUP BY project_id, event_date, event_name;

CREATE TABLE IF NOT EXISTS sessions
(
  project_id String,
  session_id String,
  user_id String,
  device_id String,
  session_start DateTime64(3),
  session_end DateTime64(3),
  duration UInt32,
  country FixedString(2),
  events UInt32
)
ENGINE = MergeTree
PARTITION BY (project_id, toYYYYMM(session_start))
ORDER BY (project_id, user_id, session_start);

-- 每日活跃用户（基于 events 表）
CREATE MATERIALIZED VIEW IF NOT EXISTS mv_dau
ENGINE = AggregatingMergeTree
PARTITION BY (project_id, toYYYYMM(event_date))
ORDER BY (project_id, event_date)
AS
SELECT
  project_id,
  event_date,
  uniqState(if(user_id != '' , user_id, device_id)) AS dau
FROM events
GROUP BY project_id, event_date;

-- Retention 按 cohort 日与偏移 d（0/1/7/30）聚合
CREATE TABLE IF NOT EXISTS retention_daily
(
  project_id String,
  cohort_date Date,
  d UInt16,
  users UInt64
)
ENGINE = SummingMergeTree
PARTITION BY (project_id, toYYYYMM(cohort_date))
ORDER BY (project_id, cohort_date, d);

-- 漏斗（两步）：按日聚合 started/completed
CREATE TABLE IF NOT EXISTS funnels_2step
(
  project_id String,
  event_date Date,
  step1 LowCardinality(String),
  step2 LowCardinality(String),
  started UInt64,
  completed UInt64
)
ENGINE = SummingMergeTree
PARTITION BY (project_id, toYYYYMM(event_date))
ORDER BY (project_id, event_date, step1, step2);
