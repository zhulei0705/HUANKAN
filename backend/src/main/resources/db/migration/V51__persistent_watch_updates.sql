CREATE TABLE watch_update(id bigserial PRIMARY KEY,owner_key char(64) NOT NULL,event_id uuid NOT NULL REFERENCES financial_event(id),evidence_id uuid REFERENCES raw_evidence(id),kind text NOT NULL CHECK(kind IN('OFFICIAL_UPDATE','RESEARCH_STATUS')),reference_id text NOT NULL,reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(owner_key,kind,reference_id));
CREATE INDEX watch_update_owner ON watch_update(owner_key,id);
CREATE TRIGGER watch_update_immutable BEFORE UPDATE OR DELETE ON watch_update FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE TABLE watch_update_read(owner_key char(64) NOT NULL,update_id bigint NOT NULL REFERENCES watch_update(id),read_at timestamptz NOT NULL DEFAULT clock_timestamp(),PRIMARY KEY(owner_key,update_id));
CREATE FUNCTION research_activity_notification() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF EXISTS(SELECT 1 FROM user_watch w WHERE w.owner_key=NEW.owner_key AND w.enabled AND w.kind='EVENT' AND w.value=NEW.event_id::text) THEN
 INSERT INTO watch_update(owner_key,event_id,kind,reference_id,reason) VALUES(NEW.owner_key,NEW.event_id,'RESEARCH_STATUS',NEW.id::text,'个人研究工作状态变化：'||NEW.state||'；'||NEW.reason) ON CONFLICT DO NOTHING;
 END IF;RETURN NEW; END $$;
CREATE TRIGGER research_activity_notification AFTER INSERT ON event_research_activity FOR EACH ROW EXECUTE FUNCTION research_activity_notification();
