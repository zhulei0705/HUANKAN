-- Qualification may finish after the collection cursor has advanced. It gets a new journal position.
CREATE TABLE notification_dispatch(id bigint PRIMARY KEY DEFAULT nextval('stream_outbox_id_seq'),
 review_id bigint NOT NULL UNIQUE REFERENCES notification_review(id),outbox_id bigint NOT NULL REFERENCES stream_outbox(id),
 fact_id uuid NOT NULL REFERENCES fact_candidate(id),message_class text NOT NULL CHECK(message_class IN('VERIFIED_FACT','IMPORTANT_ALERT')),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(outbox_id,message_class,fact_id));
CREATE OR REPLACE FUNCTION notification_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.message_class IN('VERIFIED_FACT','IMPORTANT_ALERT') AND NOT EXISTS(
 SELECT 1 FROM stream_outbox o JOIN fact_candidate f ON f.id=NEW.fact_id JOIN fact_evidence p ON p.fact_id=f.id
 JOIN raw_evidence r ON r.id=p.evidence_id JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id
 WHERE o.id=NEW.outbox_id AND p.evidence_id=o.evidence_id AND s.level='L0' AND l.status='APPROVED'
 AND l.storage_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) AND o.notification_kind<>'HISTORICAL_BACKFILL'
 AND COALESCE((SELECT decision FROM fact_review v WHERE v.fact_id=f.id ORDER BY created_at DESC,id DESC LIMIT 1),f.verification_status) IN('FIELD_MATCHED','INDEPENDENT_SUPPORTED')
 AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')) THEN RAISE EXCEPTION 'Qualified alert needs current reviewed non-backfill official atomic fact'; END IF;
 IF length(NEW.actor)<1 OR length(NEW.reason)<10 THEN RAISE EXCEPTION 'Notification review requires actor and reason';END IF;RETURN NEW;END $$;
CREATE FUNCTION dispatch_notification_review() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.message_class IN('VERIFIED_FACT','IMPORTANT_ALERT') THEN
 INSERT INTO notification_dispatch(review_id,outbox_id,fact_id,message_class) VALUES(NEW.id,NEW.outbox_id,NEW.fact_id,NEW.message_class) ON CONFLICT DO NOTHING;
 END IF;RETURN NEW;END $$;
CREATE TRIGGER dispatch_notification_review AFTER INSERT ON notification_review FOR EACH ROW EXECUTE FUNCTION dispatch_notification_review();
CREATE TRIGGER notification_dispatch_immutable BEFORE UPDATE OR DELETE ON notification_dispatch FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE VIEW stream_journal AS
SELECT q.id,q.event_id,q.evidence_id,q.created_at,q.notification_kind,q.source_id,
 CASE WHEN q.notification_kind='HISTORICAL_BACKFILL' THEN 'BACKFILL' WHEN q.notification_kind='REVISION' THEN 'REVISION' ELSE q.base_class END message_class,
 q.base_class,q.priority,q.official_eligible,false important_alert_eligible,q.qualification_reason,e.category,COALESCE(g.topics,'[]'::jsonb) topics,
 q.id source_outbox_id,false qualification_update,(g.event_id IS NOT NULL OR q.important_alert_eligible) qualification_ready
FROM notification_qualification q JOIN financial_event e ON e.id=q.event_id LEFT JOIN governed_message g ON g.event_id=e.id
UNION ALL
SELECT n.id,q.event_id,q.evidence_id,n.created_at,q.notification_kind,q.source_id,n.message_class,q.base_class,
 greatest(q.priority,CASE WHEN n.message_class='IMPORTANT_ALERT' THEN 70 ELSE q.priority END),
 q.official_eligible AND n.message_class=q.message_class AND EXISTS(SELECT 1 FROM fact_candidate f WHERE f.id=n.fact_id AND COALESCE((SELECT decision FROM fact_review v WHERE v.fact_id=f.id ORDER BY created_at DESC,id DESC LIMIT 1),f.verification_status) IN('FIELD_MATCHED','INDEPENDENT_SUPPORTED')),
 n.message_class='IMPORTANT_ALERT' AND q.important_alert_eligible AND EXISTS(SELECT 1 FROM fact_candidate f WHERE f.id=n.fact_id AND COALESCE((SELECT decision FROM fact_review v WHERE v.fact_id=f.id ORDER BY created_at DESC,id DESC LIMIT 1),f.verification_status) IN('FIELD_MATCHED','INDEPENDENT_SUPPORTED')),rn.reason,e.category,COALESCE(g.topics,'[]'::jsonb),q.id,true,true
FROM notification_dispatch n JOIN notification_qualification q ON q.id=n.outbox_id JOIN financial_event e ON e.id=q.event_id
LEFT JOIN governed_message g ON g.event_id=e.id JOIN notification_review rn ON rn.id=n.review_id;
ALTER TABLE stream_filter_batch ADD COLUMN first_cursor bigint;
ALTER TABLE stream_filter_batch ADD COLUMN last_cursor bigint;

ALTER TABLE stream_delivery DROP CONSTRAINT stream_delivery_delivery_type_check;
ALTER TABLE stream_delivery ADD CONSTRAINT stream_delivery_delivery_type_check CHECK(delivery_type IN('LIVE','REPLAY','BACKFILL','QUALIFICATION'));
