ALTER TABLE ingestion_job ADD COLUMN lease_owner uuid;
CREATE FUNCTION bind_job_lease() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN SELECT owner INTO NEW.lease_owner FROM collector_lease WHERE source_id=NEW.source_id; RETURN NEW; END $$;
CREATE TRIGGER bind_job_lease BEFORE INSERT ON ingestion_job FOR EACH ROW EXECUTE FUNCTION bind_job_lease();
ALTER TABLE gdelt_index_observation ADD COLUMN complete boolean NOT NULL DEFAULT true;
ALTER TABLE gdelt_index_observation ADD COLUMN error text;
CREATE TABLE sec_mapping_evidence(id bigserial PRIMARY KEY, acquired_at timestamptz NOT NULL DEFAULT now(), source_url text NOT NULL, raw_response bytea NOT NULL, sha256 char(64) NOT NULL CHECK(sha256=encode(sha256(raw_response),'hex')));
