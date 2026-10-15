CREATE FUNCTION enforce_source_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM ingestion_job WHERE id=NEW.job_id AND source_id=NEW.source_id) THEN
   RAISE EXCEPTION 'Evidence job source mismatch';
 END IF;
 IF NEW.source_id='FED' AND NEW.source_url !~ '^https://www[.]federalreserve[.]gov/' THEN
   RAISE EXCEPTION 'Official source URL mismatch';
 END IF;
 IF NEW.source_id='SEC' AND NEW.source_url !~ '^https://www[.]sec[.]gov/Archives/edgar/data/[0-9]+/' THEN
   RAISE EXCEPTION 'Official source URL mismatch';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_source_binding BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION enforce_source_binding();
