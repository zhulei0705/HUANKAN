ALTER TABLE source_cursor ADD COLUMN retry_after_at timestamptz;
CREATE INDEX stream_outbox_evidence ON stream_outbox(evidence_id);
CREATE FUNCTION enforce_gdelt_binding() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.source_id='GDELT' AND (NEW.source_url !~ '^https://data[.]gdeltproject[.]org/gdeltv2/[0-9]{14}[.]gkg[.]csv[.]zip$' OR NEW.verification_status<>'PENDING') THEN RAISE EXCEPTION 'Aggregated evidence must remain pending and retain batch source'; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER evidence_gdelt_binding BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION enforce_gdelt_binding();
