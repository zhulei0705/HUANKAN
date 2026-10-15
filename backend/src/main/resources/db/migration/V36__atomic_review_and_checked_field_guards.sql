ALTER TABLE fact_review ADD CONSTRAINT fact_review_decision CHECK(decision IN('SOURCE_CHECKED','FIELD_MATCHED','INDEPENDENT_SUPPORTED','CONFLICT','INSUFFICIENT','REJECTED'));
ALTER TABLE fact_review ADD COLUMN excerpt text NOT NULL;
CREATE FUNCTION checked_atomic_fields_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.verification_status='FIELD_MATCHED' AND NOT EXISTS(SELECT 1 FROM macro_observation m JOIN macro_series s ON s.id=m.series_id
 WHERE m.evidence_id=NEW.observation_id AND NEW.fact_type='MACRO_OBSERVATION' AND NEW.fields->>'indicator_id'=m.series_id
 AND NEW.fields->>'observation_period'=m.observation_date::text AND (NEW.fields->>'actual_value')::numeric IS NOT DISTINCT FROM m.value
 AND NEW.fields->>'unit'=s.units AND NEW.fields->>'frequency'=s.frequency AND NEW.version=m.version)
 THEN RAISE EXCEPTION 'Checked macro fields must match immutable observation ledger';END IF;
 IF NEW.verification_status='INDEPENDENT_SUPPORTED' THEN RAISE EXCEPTION 'Independent support requires separate review history';END IF;
 RETURN NEW;END $$;
CREATE TRIGGER checked_atomic_fields_guard BEFORE INSERT ON fact_candidate FOR EACH ROW EXECUTE FUNCTION checked_atomic_fields_guard();
CREATE FUNCTION atomic_review_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF length(NEW.actor)<1 OR length(NEW.reason)<10 OR length(NEW.excerpt)<5 OR NOT EXISTS(
 SELECT 1 FROM raw_evidence r JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id
 WHERE r.id=NEW.evidence_id AND s.level='L0' AND l.status='APPROVED' AND l.storage_allowed
 AND (l.valid_until IS NULL OR l.valid_until>now()) AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')
 AND position(NEW.excerpt in convert_from(r.raw_content,'UTF8'))>0) THEN RAISE EXCEPTION 'Fact review requires actual official excerpt and accountable reason';END IF;
 IF NEW.decision='INDEPENDENT_SUPPORTED' AND NOT EXISTS(SELECT 1 FROM fact_evidence p JOIN raw_evidence a ON a.id=p.evidence_id
 JOIN raw_evidence b ON b.id=NEW.evidence_id WHERE p.fact_id=NEW.fact_id AND a.source_id<>b.source_id AND a.raw_content_hash<>b.raw_content_hash)
 THEN RAISE EXCEPTION 'Same source or response cannot establish independent support';END IF;
 IF NEW.decision='FIELD_MATCHED' AND NOT EXISTS(SELECT 1 FROM fact_candidate f WHERE f.id=NEW.fact_id AND f.verification_status='FIELD_MATCHED')
 THEN RAISE EXCEPTION 'Field matching requires structured extractor validation';END IF;
 RETURN NEW;END $$;
CREATE TRIGGER atomic_review_guard BEFORE INSERT ON fact_review FOR EACH ROW EXECUTE FUNCTION atomic_review_guard();
CREATE TRIGGER atomic_review_immutable BEFORE UPDATE OR DELETE ON fact_review FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
