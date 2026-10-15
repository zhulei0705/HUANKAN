INSERT INTO source_registry(id,name,level,endpoint,enabled,interval_seconds,region) VALUES('BLS','美国劳工统计局','L0','https://api.bls.gov/publicAPI/v1/timeseries/data/',false,86400,'美国');
INSERT INTO source_license(source_id,terms_url) VALUES('BLS','https://www.bls.gov/developers/termsOfService.htm');
CREATE TABLE bls_series_config(id text PRIMARY KEY,title text NOT NULL,unit text,frequency text NOT NULL DEFAULT 'M',adjustment text NOT NULL,definition_url text NOT NULL,verified boolean NOT NULL DEFAULT false,enabled boolean NOT NULL DEFAULT false,CHECK(NOT enabled OR verified AND unit IS NOT NULL));
INSERT INTO bls_series_config VALUES
 ('CUUR0000SA0','美国CPI全项目（未季调）','指数，1982-84=100','M','未季调','https://data.bls.gov/timeseries/CUUR0000SA0',true,true),
 ('LNS14000000','美国失业率（季调）','百分比','M','季调','https://data.bls.gov/timeseries/LNS14000000',true,true),
 ('CES0000000001','美国非农就业总人数（季调）','千人','M','季调','https://data.bls.gov/timeseries/CES0000000001',true,true),
 ('CUSR0000SA0L1E','美国核心CPI（季调，不含食品和能源）',NULL,'M','季调','https://data.bls.gov/timeseries/CUSR0000SA0L1E',false,false),
 ('WPUFD4','美国PPI最终需求（未季调）',NULL,'M','未季调','https://data.bls.gov/timeseries/WPUFD4',false,false);
CREATE TABLE bls_request(id uuid PRIMARY KEY,job_id uuid NOT NULL REFERENCES ingestion_job(id),request_body jsonb NOT NULL,started_at timestamptz NOT NULL DEFAULT clock_timestamp(),finished_at timestamptz,http_status integer,api_status text,raw_response bytea,sha256 char(64),error text,CHECK(raw_response IS NULL OR sha256=encode(sha256(raw_response),'hex')));
CREATE TABLE bls_observation(evidence_id uuid PRIMARY KEY REFERENCES macro_observation(evidence_id),request_id uuid NOT NULL REFERENCES bls_request(id),period text NOT NULL,raw_value text NOT NULL,footnotes jsonb NOT NULL,release_status text NOT NULL CHECK(release_status IN('PRELIMINARY','REVISED_NOTE','UNKNOWN')),payload jsonb NOT NULL);
CREATE TRIGGER bls_observation_immutable BEFORE UPDATE OR DELETE ON bls_observation FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE FUNCTION bls_source_guard() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF NEW.source_id='BLS' AND (NEW.source_url !~ '^https://api[.]bls[.]gov/publicAPI/v1/timeseries/data/$' OR NEW.published_at IS NOT NULL OR NEW.verification_status<>'PENDING' OR NEW.ingestion_kind='NEW_EVENT' OR NOT EXISTS(SELECT 1 FROM bls_request b WHERE b.job_id=NEW.job_id AND b.http_status=200 AND b.api_status='REQUEST_SUCCEEDED' AND b.sha256=NEW.raw_content_hash)) THEN RAISE EXCEPTION 'BLS API observations have no verified news release timestamp'; END IF;RETURN NEW;END $$;
CREATE TRIGGER bls_source_guard BEFORE INSERT ON raw_evidence FOR EACH ROW EXECUTE FUNCTION bls_source_guard();
