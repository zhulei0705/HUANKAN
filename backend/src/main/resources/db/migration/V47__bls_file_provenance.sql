CREATE TABLE bls_file_observation(evidence_id uuid PRIMARY KEY REFERENCES macro_observation(evidence_id),data_response_id uuid NOT NULL REFERENCES official_resource_response(id),series_response_id uuid NOT NULL REFERENCES official_resource_response(id),footnote_response_id uuid NOT NULL REFERENCES official_resource_response(id),raw_line text NOT NULL,footnotes jsonb NOT NULL,metadata jsonb NOT NULL);
CREATE TRIGGER bls_file_observation_immutable BEFORE UPDATE OR DELETE ON bls_file_observation FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE OR REPLACE FUNCTION bls_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.source_id='BLS' THEN
  IF NEW.published_at IS NOT NULL OR NEW.verification_status<>'PENDING' OR NEW.ingestion_kind='NEW_EVENT' THEN RAISE EXCEPTION 'BLS observations do not establish a first publication time';END IF;
  IF NEW.source_url='https://api.bls.gov/publicAPI/v1/timeseries/data/' THEN
   IF NOT EXISTS(SELECT 1 FROM bls_request b WHERE b.job_id=NEW.job_id AND b.http_status=200 AND b.api_status='REQUEST_SUCCEEDED' AND b.sha256=NEW.raw_content_hash) THEN RAISE EXCEPTION 'BLS API evidence requires actual successful response';END IF;
  ELSIF NEW.source_url='https://download.bls.gov/pub/time.series/cu/cu.data.1.AllItems' THEN
   IF NOT EXISTS(SELECT 1 FROM official_resource_response r JOIN official_resource_policy p ON p.url=r.url WHERE r.url=NEW.source_url AND r.http_status=200 AND r.error IS NULL AND r.sha256=NEW.raw_content_hash AND p.access_allowed AND p.storage_allowed AND p.display_allowed) THEN RAISE EXCEPTION 'BLS bulk evidence requires licensed successful bounded response';END IF;
  ELSE RAISE EXCEPTION 'Unknown BLS evidence path';END IF;
 END IF;RETURN NEW;END $$;
