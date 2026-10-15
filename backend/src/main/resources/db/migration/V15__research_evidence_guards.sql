CREATE TRIGGER official_response_immutable BEFORE UPDATE OR DELETE ON official_response FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE TRIGGER macro_observation_immutable BEFORE UPDATE OR DELETE ON macro_observation FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE FUNCTION enforce_macro_source() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.source_id='NBS' AND NEW.source_url !~ '^https://www[.]stats[.]gov[.]cn/' THEN RAISE EXCEPTION 'NBS official source mismatch'; END IF;
 IF NEW.source_id='ECB_DATA' AND NEW.source_url !~ '^https://data-api[.]ecb[.]europa[.]eu/service/data/EXR/D[.](USD|JPY|GBP|CNY)[.]EUR[.]SP00[.]A([?].*)?$' THEN RAISE EXCEPTION 'ECB official series source mismatch'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER macro_source_guard BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION enforce_macro_source();
CREATE FUNCTION official_response_job_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM ingestion_job WHERE id=NEW.job_id AND source_id=NEW.source_id) THEN RAISE EXCEPTION 'Official response job source mismatch'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER official_response_source_guard BEFORE INSERT ON official_response FOR EACH ROW EXECUTE FUNCTION official_response_job_guard();
CREATE OR REPLACE FUNCTION research_verified_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.status='VERIFIED' AND NOT EXISTS(SELECT 1 FROM raw_evidence r JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id WHERE r.id=NEW.evidence_id AND s.level='L0' AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')) THEN RAISE EXCEPTION 'Verified research requires licensed intact official evidence'; END IF;
 RETURN NEW; END $$;
CREATE FUNCTION candidate_verified_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.verification='VERIFIED' AND NOT EXISTS(SELECT 1 FROM candidate_official_evidence c JOIN raw_evidence r ON r.id=c.evidence_id JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id WHERE c.event_id=NEW.event_id AND s.level='L0' AND l.status='APPROVED' AND (l.valid_until IS NULL OR l.valid_until>now()) AND r.raw_content_hash=encode(sha256(r.raw_content),'hex')) THEN RAISE EXCEPTION 'Verified event needs linked official evidence'; END IF;
 RETURN NEW; END $$;
CREATE TRIGGER candidate_fact_guard BEFORE INSERT OR UPDATE ON event_candidate FOR EACH ROW EXECUTE FUNCTION candidate_verified_guard();
