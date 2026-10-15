-- Preserve the collection outbox. Qualification is a separate, reproducible view.
CREATE VIEW notification_qualification AS
SELECT o.id,o.event_id,o.evidence_id,o.created_at,o.notification_kind,e.source_id,
 CASE WHEN o.notification_kind='HISTORICAL_BACKFILL' THEN 'BACKFILL'
      WHEN o.notification_kind='REVISION' THEN 'REVISION'
      WHEN s.level='L2' THEN 'RESEARCH_CANDIDATE' ELSE 'SOURCE_EVENT' END message_class,
 CASE WHEN s.level='L2' THEN 'RESEARCH_CANDIDATE' ELSE 'SOURCE_EVENT' END base_class,
 COALESCE(g.priority,0) priority,
 (s.level='L0' AND e.source_id NOT IN('ECB_DATA','FRED') AND o.notification_kind<>'HISTORICAL_BACKFILL') official_eligible,
 false important_alert_eligible,
 CASE WHEN o.notification_kind='HISTORICAL_BACKFILL' THEN '历史采集默认不提醒'
      WHEN s.level='L2' THEN '聚合线索须主动订阅，不等同官方事实'
      ELSE '官方来源消息，重大程度与独立核实另行审核' END qualification_reason
FROM stream_outbox o JOIN financial_event e ON e.id=o.event_id
JOIN source_registry s ON s.id=e.source_id LEFT JOIN governed_message g ON g.event_id=e.id;
ALTER TABLE stream_connection ADD COLUMN subscription_scope text NOT NULL DEFAULT 'LEGACY'
 CHECK(subscription_scope IN('LEGACY','OFFICIAL','LEADS','HISTORY'));
CREATE TABLE stream_filter_batch(id bigserial PRIMARY KEY,connection_id uuid NOT NULL REFERENCES stream_connection(id),
 first_outbox bigint NOT NULL REFERENCES stream_outbox(id),last_outbox bigint NOT NULL REFERENCES stream_outbox(id),
 scanned integer NOT NULL,sent integer NOT NULL,excluded integer NOT NULL,classes jsonb NOT NULL,
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),CHECK(scanned=sent+excluded));
