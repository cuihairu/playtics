-- Faster experiment queries using materialized tables

-- Daily exposures by variant (from exp_exposure_users)
CREATE OR REPLACE VIEW v_exp_exposures_by_day_fast AS
SELECT project_id, toDate(expose_ts) AS event_date,
       exp, variant,
       count() AS exposures,
       uniqExact(uid) AS users
FROM exp_exposure_users
GROUP BY project_id, event_date, exp, variant;

-- 24h conversion by variant (join first-level-complete)
CREATE OR REPLACE VIEW v_exp_conversion_24h_fast AS
SELECT e.project_id, toDate(e.expose_ts) AS exposure_date, e.exp, e.variant,
       count() AS exposed_users,
       countIf(c.conv_ts >= e.expose_ts AND c.conv_ts <= e.expose_ts + INTERVAL 24 HOUR) AS converted_users,
       round(converted_users / nullIf(exposed_users,0), 4) AS cr_24h
FROM exp_exposure_users e
LEFT JOIN exp_first_level_complete c ON c.project_id=e.project_id AND c.uid=e.uid
GROUP BY e.project_id, exposure_date, e.exp, e.variant;

-- 7d conversion by variant (join first-level-complete)
CREATE OR REPLACE VIEW v_exp_conversion_7d_fast AS
SELECT e.project_id, toDate(e.expose_ts) AS exposure_date, e.exp, e.variant,
       count() AS exposed_users,
       countIf(c.conv_ts >= e.expose_ts AND c.conv_ts <= e.expose_ts + INTERVAL 7 DAY) AS converted_users,
       round(converted_users / nullIf(exposed_users,0), 4) AS cr_7d
FROM exp_exposure_users e
LEFT JOIN exp_first_level_complete c ON c.project_id=e.project_id AND c.uid=e.uid
GROUP BY e.project_id, exposure_date, e.exp, e.variant;
