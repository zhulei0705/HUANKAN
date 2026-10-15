BEGIN READ ONLY;
SET LOCAL statement_timeout='15s';
WITH periods(name,start_at,end_at) AS (VALUES
 ('早间页面与健康探针',timestamptz '2026-10-13T00:44:00Z',timestamptz '2026-10-13T00:57:00Z'),
 ('午间页面编译错误',timestamptz '2026-10-13T05:19:00Z',timestamptz '2026-10-13T05:25:00Z'))
SELECT jsonb_build_object('queried_at',clock_timestamp(),
 'periods',(SELECT jsonb_agg(jsonb_build_object('name',p.name,'start',p.start_at,'end',p.end_at,
 'jobs',(SELECT jsonb_agg(j) FROM(SELECT source_id,status,started_at,finished_at,duration_ms,error_code,http_status FROM ingestion_job WHERE started_at BETWEEN p.start_at AND p.end_at ORDER BY started_at)j),
 'source_requests',(SELECT jsonb_agg(h) FROM(SELECT source_id,attempted_at,completed_at,status,duration_ms,error FROM http_request_observation WHERE attempted_at BETWEEN p.start_at AND p.end_at ORDER BY attempted_at)h),
 'stream_connections',(SELECT jsonb_agg(c) FROM(SELECT id,opened_at,last_activity_at,closed_at,close_kind,initial_cursor FROM stream_connection WHERE opened_at<=p.end_at AND coalesce(closed_at,'infinity'::timestamptz)>=p.start_at ORDER BY opened_at)c),
 'delivery_outcomes',(SELECT jsonb_agg(d) FROM(SELECT delivery_type,replay,outcome,count(*) records,count(client_received_at) client_received,count(acknowledged_at) acknowledged FROM stream_delivery WHERE sent_at BETWEEN p.start_at AND p.end_at GROUP BY delivery_type,replay,outcome)d),
 'render_receipts',(SELECT count(*) FROM browser_delivery_observation WHERE acknowledged_at BETWEEN p.start_at AND p.end_at))) FROM periods p),
 'current',jsonb_build_object('database_start',pg_postmaster_start_time(),'connections',(SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()),'audit_cursor_backlog',(SELECT greatest(coalesce((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT'),
 'eligible_official_new_events',(SELECT count(*) FROM stream_outbox o JOIN raw_evidence r ON r.id=o.evidence_id JOIN source_registry s ON s.id=r.source_id WHERE o.notification_kind='NEW_EVENT' AND s.level='L0' AND EXISTS(SELECT 1 FROM notification_qualification q WHERE q.id=o.id AND q.important_alert_eligible)),
 'official_live_deliveries',(SELECT count(*) FROM stream_delivery d JOIN stream_outbox o ON o.id=d.outbox_id JOIN raw_evidence r ON r.id=o.evidence_id JOIN source_registry s ON s.id=r.source_id WHERE d.sent_at>='2026-10-12T11:47:54Z' AND d.delivery_type='LIVE' AND NOT d.replay AND o.notification_kind='NEW_EVENT' AND s.level='L0'),
 'browser_live_receipts',(SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id JOIN stream_outbox o ON o.id=d.outbox_id JOIN raw_evidence r ON r.id=o.evidence_id JOIN source_registry s ON s.id=r.source_id WHERE b.acknowledged_at>='2026-10-12T11:47:54Z' AND d.delivery_type='LIVE' AND NOT d.replay AND o.notification_kind='NEW_EVENT' AND s.level='L0')));
COMMIT;
