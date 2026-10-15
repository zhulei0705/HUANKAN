ALTER TABLE stream_connection ADD COLUMN subscribed_sources text[] NOT NULL DEFAULT '{}';
ALTER TABLE stream_connection ADD COLUMN minimum_priority integer NOT NULL DEFAULT 0 CHECK(minimum_priority BETWEEN 0 AND 100);
CREATE TABLE notification_review(id bigserial PRIMARY KEY,outbox_id bigint NOT NULL REFERENCES stream_outbox(id),
 message_class text NOT NULL CHECK(message_class IN('SOURCE_EVENT','RESEARCH_CANDIDATE','VERIFIED_FACT','IMPORTANT_ALERT','BACKFILL','REVISION')),
 fact_id uuid REFERENCES fact_candidate(id),actor text NOT NULL,reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE FUNCTION notification_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.message_class IN('VERIFIED_FACT','IMPORTANT_ALERT') AND NOT EXISTS(
 SELECT 1 FROM stream_outbox o JOIN fact_candidate f ON f.id=NEW.fact_id JOIN fact_evidence p ON p.fact_id=f.id
 JOIN raw_evidence r ON r.id=p.evidence_id JOIN source_registry s ON s.id=r.source_id
 WHERE o.id=NEW.outbox_id AND p.evidence_id=o.evidence_id AND s.level='L0'
 AND o.notification_kind<>'HISTORICAL_BACKFILL' AND f.verification_status IN('FIELD_MATCHED','INDEPENDENT_SUPPORTED')
 AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')) THEN RAISE EXCEPTION 'Qualified alert needs non-backfill official checked atomic fact'; END IF;
 IF length(NEW.actor)<1 OR length(NEW.reason)<10 THEN RAISE EXCEPTION 'Notification review requires actor and reason';END IF;RETURN NEW;END $$;
CREATE TRIGGER notification_review_guard BEFORE INSERT ON notification_review FOR EACH ROW EXECUTE FUNCTION notification_review_guard();
CREATE TRIGGER notification_review_immutable BEFORE UPDATE OR DELETE ON notification_review FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE OR REPLACE VIEW notification_qualification AS
SELECT o.id,o.event_id,o.evidence_id,o.created_at,o.notification_kind,e.source_id,
 COALESCE(n.message_class,CASE WHEN o.notification_kind='HISTORICAL_BACKFILL' THEN 'BACKFILL' WHEN o.notification_kind='REVISION' THEN 'REVISION' WHEN s.level='L2' THEN 'RESEARCH_CANDIDATE' ELSE 'SOURCE_EVENT' END) message_class,
 CASE WHEN s.level='L2' THEN 'RESEARCH_CANDIDATE' ELSE 'SOURCE_EVENT' END base_class,
 COALESCE(g.priority,0) priority,
 (s.level='L0' AND e.source_id NOT IN('ECB_DATA','FRED') AND o.notification_kind<>'HISTORICAL_BACKFILL') official_eligible,
 COALESCE(n.message_class='IMPORTANT_ALERT',false) important_alert_eligible,
 COALESCE(n.reason,CASE WHEN o.notification_kind='HISTORICAL_BACKFILL' THEN '历史采集默认不提醒' WHEN s.level='L2' THEN '聚合线索须主动订阅，不等同官方事实' ELSE '官方来源消息，重大程度与独立核实另行审核' END) qualification_reason
FROM stream_outbox o JOIN financial_event e ON e.id=o.event_id JOIN source_registry s ON s.id=e.source_id
LEFT JOIN governed_message g ON g.event_id=e.id LEFT JOIN LATERAL(SELECT message_class,reason FROM notification_review WHERE outbox_id=o.id ORDER BY created_at DESC,id DESC LIMIT 1)n ON true;
