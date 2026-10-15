CREATE FUNCTION enforce_evidence_quality() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF encode(sha256(NEW.raw_content),'hex') <> NEW.raw_content_hash THEN
   RAISE EXCEPTION 'Evidence hash mismatch';
 END IF;
 IF NEW.verification_status='VERIFIED' THEN
   IF NOT EXISTS(SELECT 1 FROM source_registry s JOIN source_license l ON s.id=l.source_id WHERE s.id=NEW.source_id AND s.level='L0' AND l.status='APPROVED' AND l.access_allowed AND l.storage_allowed AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now())) THEN
     RAISE EXCEPTION 'Verified evidence requires official licensed source';
   END IF;
   IF NEW.published_at IS NULL THEN RAISE EXCEPTION 'Verified evidence requires publication time'; END IF;
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_quality BEFORE INSERT OR UPDATE ON raw_evidence FOR EACH ROW EXECUTE FUNCTION enforce_evidence_quality();
CREATE FUNCTION protect_raw_evidence() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Evidence is immutable; create a new version'; END $$;
CREATE TRIGGER evidence_immutable BEFORE UPDATE OR DELETE ON raw_evidence FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
