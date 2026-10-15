ALTER TABLE user_watch DROP CONSTRAINT user_watch_kind_check;
ALTER TABLE user_watch ADD CONSTRAINT user_watch_kind_check CHECK(kind IN('COUNTRY','CENTRAL_BANK','INDICATOR','INSTITUTION','COMPANY','INDUSTRY','EVENT_TYPE','SOURCE','EVENT'));
CREATE OR REPLACE FUNCTION watch_matches(owner char(64),evidence uuid,event uuid,source text,level text,priority integer) RETURNS boolean LANGUAGE sql STABLE AS $$
 SELECT EXISTS(SELECT 1 FROM user_watch w JOIN source_registry s ON s.id=source WHERE w.owner_key=owner AND w.enabled AND priority>=w.minimum_priority AND (level<>'L2' OR w.include_leads) AND CASE w.kind
 WHEN 'EVENT' THEN w.value=event::text
 WHEN 'SOURCE' THEN w.value=source
 WHEN 'CENTRAL_BANK' THEN w.value=source AND source IN('FED','ECB')
 WHEN 'COUNTRY' THEN w.value=s.region
 WHEN 'INDICATOR' THEN EXISTS(SELECT 1 FROM macro_observation m WHERE m.evidence_id=evidence AND m.series_id=w.value)
 WHEN 'COMPANY' THEN EXISTS(SELECT 1 FROM gleif_legal_record g WHERE g.evidence_id=evidence AND g.lei=w.value)
 WHEN 'INSTITUTION' THEN w.value=s.name OR EXISTS(SELECT 1 FROM gleif_legal_record g WHERE g.evidence_id=evidence AND g.legal_name=w.value)
 WHEN 'INDUSTRY' THEN EXISTS(SELECT 1 FROM governed_message g WHERE g.event_id=event AND g.topics ? w.value)
 WHEN 'EVENT_TYPE' THEN EXISTS(SELECT 1 FROM financial_event e WHERE e.id=event AND e.category=w.value) ELSE false END);
$$;

CREATE TABLE event_research_activity(id uuid PRIMARY KEY,event_id uuid NOT NULL REFERENCES financial_event(id),owner_key char(64) NOT NULL,state text NOT NULL CHECK(state IN('RESEARCHING','ARCHIVED')),reason text NOT NULL CHECK(length(reason)>=10),created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE INDEX event_research_activity_owner ON event_research_activity(event_id,owner_key,created_at);
CREATE TRIGGER event_research_activity_immutable BEFORE UPDATE OR DELETE ON event_research_activity FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
