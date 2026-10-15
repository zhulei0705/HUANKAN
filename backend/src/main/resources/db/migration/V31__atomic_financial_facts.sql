CREATE TABLE fact_candidate(id uuid PRIMARY KEY,fact_key text NOT NULL,version integer NOT NULL CHECK(version>0),
 fact_type text NOT NULL CHECK(fact_type IN('OFFICIAL_PUBLICATION','CENTRAL_BANK_DECISION','MACRO_OBSERVATION','FINANCIAL_DISCLOSURE','BUSINESS_ANNOUNCEMENT','LEGAL_IDENTITY_ATTRIBUTE','DOCUMENT_REVISION')),
 event_id uuid REFERENCES financial_event(id),observation_id uuid REFERENCES macro_observation(evidence_id),
 fields jsonb NOT NULL,extraction_method text NOT NULL,verification_status text NOT NULL DEFAULT 'EXTRACTED'
 CHECK(verification_status IN('EXTRACTED','SOURCE_CHECKED','FIELD_MATCHED','INDEPENDENT_SUPPORTED','CONFLICT','REVISED','INSUFFICIENT','REJECTED')),
 available_at timestamptz,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(fact_key,version));
CREATE TABLE fact_evidence(fact_id uuid NOT NULL REFERENCES fact_candidate(id),evidence_id uuid NOT NULL REFERENCES raw_evidence(id),
 excerpt text NOT NULL,source_locator text NOT NULL,PRIMARY KEY(fact_id,evidence_id));
CREATE TABLE fact_review(id bigserial PRIMARY KEY,fact_id uuid NOT NULL REFERENCES fact_candidate(id),decision text NOT NULL,
 actor text NOT NULL,reason text NOT NULL,evidence_id uuid NOT NULL REFERENCES raw_evidence(id),created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TABLE fact_revision(fact_id uuid PRIMARY KEY REFERENCES fact_candidate(id),previous_id uuid NOT NULL REFERENCES fact_candidate(id),reason text NOT NULL,CHECK(fact_id<>previous_id));
CREATE TABLE fact_conflict(id uuid PRIMARY KEY,left_fact uuid NOT NULL REFERENCES fact_candidate(id),right_fact uuid NOT NULL REFERENCES fact_candidate(id),reason text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp(),CHECK(left_fact<>right_fact));
CREATE INDEX atomic_fact_page ON fact_candidate(created_at DESC,id);
CREATE TRIGGER atomic_fact_immutable BEFORE UPDATE OR DELETE ON fact_candidate FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE TRIGGER atomic_support_immutable BEFORE UPDATE OR DELETE ON fact_evidence FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE FUNCTION atomic_support_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM raw_evidence r JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id
 WHERE r.id=NEW.evidence_id AND s.level='L0' AND l.status='APPROVED' AND l.storage_allowed
 AND (l.valid_until IS NULL OR l.valid_until>now()) AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')
 AND length(NEW.excerpt)>4 AND position(NEW.excerpt in convert_from(r.raw_content,'UTF8'))>0)
 THEN RAISE EXCEPTION 'Atomic fact needs licensed intact literal official evidence'; END IF; RETURN NEW; END $$;
CREATE TRIGGER atomic_support_guard BEFORE INSERT ON fact_evidence FOR EACH ROW EXECUTE FUNCTION atomic_support_guard();
