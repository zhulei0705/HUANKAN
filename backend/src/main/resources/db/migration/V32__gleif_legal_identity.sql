INSERT INTO source_registry(id,name,level,endpoint,enabled,interval_seconds,region) VALUES('GLEIF','全球法人识别编码基金会','L0','https://api.gleif.org/api/v1/',false,86400,'全球');
INSERT INTO source_license(source_id,terms_url) VALUES('GLEIF','https://www.gleif.org/en/meta/lei-data-terms-of-use');
CREATE TABLE gleif_request(id uuid PRIMARY KEY,job_id uuid NOT NULL REFERENCES ingestion_job(id),url text NOT NULL,
 status integer,raw_response bytea,sha256 char(64),error text,acquired_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 CHECK(raw_response IS NULL OR sha256=encode(sha256(raw_response),'hex')));
CREATE TABLE gleif_legal_record(evidence_id uuid PRIMARY KEY REFERENCES raw_evidence(id),entity_id uuid NOT NULL REFERENCES financial_entity(id),
 lei char(20) NOT NULL,legal_name text NOT NULL,jurisdiction text,legal_address jsonb NOT NULL,entity_status text,
 registration_status text,last_update timestamptz,attributes jsonb NOT NULL,UNIQUE(lei,evidence_id));
CREATE INDEX gleif_lei_versions ON gleif_legal_record(lei,last_update DESC,evidence_id);
CREATE TABLE gleif_level2(evidence_id uuid PRIMARY KEY REFERENCES raw_evidence(id),child_lei char(20) NOT NULL,
 kind text NOT NULL CHECK(kind IN('DIRECT_ACCOUNTING_PARENT','ULTIMATE_ACCOUNTING_PARENT','DIRECT_REPORTING_EXCEPTION','ULTIMATE_REPORTING_EXCEPTION')),
 parent_lei char(20),details jsonb NOT NULL,limitation text NOT NULL);
CREATE TRIGGER gleif_legal_immutable BEFORE UPDATE OR DELETE ON gleif_legal_record FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE TRIGGER gleif_level2_immutable BEFORE UPDATE OR DELETE ON gleif_level2 FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE FUNCTION gleif_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.source_id='GLEIF' AND NEW.source_url !~ '^https://api[.]gleif[.]org/api/v1/lei-records/[A-Z0-9]{18}[0-9]{2}(/(direct|ultimate)-parent-(relationship|reporting-exception))?$' THEN RAISE EXCEPTION 'GLEIF source mismatch';END IF; RETURN NEW; END $$;
CREATE TRIGGER gleif_source_guard BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION gleif_source_guard();
