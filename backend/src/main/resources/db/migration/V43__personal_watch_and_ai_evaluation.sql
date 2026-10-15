CREATE TABLE user_watch(id uuid PRIMARY KEY,owner_key char(64) NOT NULL,kind text NOT NULL CHECK(kind IN('COUNTRY','CENTRAL_BANK','INDICATOR','INSTITUTION','COMPANY','INDUSTRY','EVENT_TYPE','SOURCE')),value text NOT NULL,label text NOT NULL,include_leads boolean NOT NULL DEFAULT false,minimum_priority integer NOT NULL DEFAULT 0 CHECK(minimum_priority BETWEEN 0 AND 100),enabled boolean NOT NULL DEFAULT true,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(owner_key,kind,value));
CREATE INDEX user_watch_owner ON user_watch(owner_key,enabled);
ALTER TABLE stream_connection ADD COLUMN watch_owner_key char(64);
CREATE TABLE ai_evaluation_case(id uuid PRIMARY KEY,evidence_id uuid NOT NULL REFERENCES raw_evidence(id),event_id uuid REFERENCES financial_event(id),task_type text NOT NULL,expected_fields jsonb NOT NULL,evidence_hash char(64) NOT NULL,prompt_version text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(evidence_id,task_type,prompt_version));
CREATE TRIGGER ai_evaluation_immutable BEFORE UPDATE OR DELETE ON ai_evaluation_case FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();

CREATE FUNCTION watch_matches(owner char(64),evidence uuid,event uuid,source text,level text,priority integer) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM user_watch w JOIN source_registry s ON s.id=source WHERE w.owner_key=owner AND w.enabled AND priority>=w.minimum_priority AND (level<>'L2' OR w.include_leads) AND CASE w.kind
 WHEN 'SOURCE' THEN w.value=source
 WHEN 'CENTRAL_BANK' THEN w.value=source AND source IN('FED','ECB')
 WHEN 'COUNTRY' THEN w.value=s.region
 WHEN 'INDICATOR' THEN EXISTS(SELECT 1 FROM macro_observation m WHERE m.evidence_id=evidence AND m.series_id=w.value)
 WHEN 'COMPANY' THEN EXISTS(SELECT 1 FROM gleif_legal_record g WHERE g.evidence_id=evidence AND g.lei=w.value)
 WHEN 'INSTITUTION' THEN w.value=s.name OR EXISTS(SELECT 1 FROM gleif_legal_record g WHERE g.evidence_id=evidence AND g.legal_name=w.value)
 WHEN 'INDUSTRY' THEN EXISTS(SELECT 1 FROM governed_message g WHERE g.event_id=event AND g.topics ? w.value)
 WHEN 'EVENT_TYPE' THEN EXISTS(SELECT 1 FROM financial_event e WHERE e.id=event AND e.category=w.value) ELSE false END);
$$;
