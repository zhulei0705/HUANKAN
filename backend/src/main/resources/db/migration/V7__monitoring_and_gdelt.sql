ALTER TABLE ingestion_job ADD COLUMN error_detail text;
CREATE TABLE gdelt_batch (
 batch_id varchar(14) PRIMARY KEY, file_url text NOT NULL, expected_bytes bigint NOT NULL,
 expected_md5 char(32) NOT NULL, raw_zip bytea, sha256 char(64),
 status varchar(20) NOT NULL CHECK(status IN('DOWNLOADING','PROCESSING','SUCCESS','FAILED')),
 etag text, completed_lines integer NOT NULL DEFAULT 0, total_lines integer,
 retained_count integer NOT NULL DEFAULT 0, excluded_count integer NOT NULL DEFAULT 0,
 collected_at timestamptz NOT NULL DEFAULT now(),finished_at timestamptz,error text
);
CREATE TABLE aggregated_dependency (
 evidence_id uuid PRIMARY KEY REFERENCES raw_evidence(id),batch_id varchar(14) REFERENCES gdelt_batch(batch_id),
 underlying_url text NOT NULL,themes text NOT NULL,organizations text NOT NULL,countries text NOT NULL,
 batch_processed_at timestamptz NOT NULL,original_publication_verified boolean NOT NULL DEFAULT false CHECK(NOT original_publication_verified)
);
CREATE TABLE event_candidate_link (
 aggregate_event_id uuid REFERENCES financial_event(id),official_event_id uuid REFERENCES financial_event(id),
 basis text NOT NULL,verification varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(verification='PENDING'),
 PRIMARY KEY(aggregate_event_id,official_event_id)
);
