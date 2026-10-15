CREATE TABLE source_registry (
 id text PRIMARY KEY, name text NOT NULL, level text NOT NULL CHECK (level IN ('L0','L1','L2')),
 endpoint text NOT NULL, enabled boolean NOT NULL DEFAULT false, interval_seconds integer NOT NULL CHECK(interval_seconds >= 60)
);
CREATE TABLE source_license (
 source_id text PRIMARY KEY REFERENCES source_registry(id), status text NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','APPROVED','DISABLED')),
 access_allowed boolean NOT NULL DEFAULT false, storage_allowed boolean NOT NULL DEFAULT false,
 display_allowed boolean NOT NULL DEFAULT false, terms_url text NOT NULL, review_basis text,
 reviewed_at timestamptz, valid_until timestamptz,
 CHECK(status <> 'APPROVED' OR (access_allowed AND storage_allowed AND display_allowed AND reviewed_at IS NOT NULL AND review_basis IS NOT NULL))
);
CREATE TABLE ingestion_job (
 id uuid PRIMARY KEY, source_id text NOT NULL REFERENCES source_registry(id), started_at timestamptz NOT NULL DEFAULT now(),
 finished_at timestamptz, status text NOT NULL, http_status integer, new_count integer NOT NULL DEFAULT 0,
 error_code text, duration_ms bigint
);
CREATE TABLE source_cursor (
 source_id text PRIMARY KEY REFERENCES source_registry(id), etag text, last_modified text, last_checked_at timestamptz
);
CREATE TABLE raw_evidence (
 id uuid PRIMARY KEY, source_id text NOT NULL REFERENCES source_registry(id), source_document_id text NOT NULL,
 source_url text NOT NULL CHECK(source_url LIKE 'https://%'), published_at timestamptz, missing_time_reason text,
 first_seen_at timestamptz NOT NULL, ingested_at timestamptz NOT NULL DEFAULT now(),
 raw_content bytea NOT NULL, raw_content_hash char(64) NOT NULL CHECK(raw_content_hash ~ '^[0-9a-f]{64}$'),
 item_fingerprint char(64) NOT NULL,
 content_language text NOT NULL, parser_version text NOT NULL, revision integer NOT NULL CHECK(revision > 0),
 verification_status text NOT NULL CHECK(verification_status IN ('PENDING','VERIFIED','QUARANTINED')),
 job_id uuid NOT NULL REFERENCES ingestion_job(id),
 UNIQUE(source_id, source_document_id, revision),
 CHECK(published_at IS NOT NULL OR missing_time_reason IS NOT NULL)
);
CREATE TABLE evidence_version (
 evidence_id uuid PRIMARY KEY REFERENCES raw_evidence(id), previous_id uuid REFERENCES raw_evidence(id), change_kind text NOT NULL
);
CREATE TABLE financial_event (
 id uuid PRIMARY KEY, source_id text NOT NULL REFERENCES source_registry(id), document_id text NOT NULL,
 original_title text NOT NULL, chinese_title text, translation_status text NOT NULL DEFAULT 'NOT_CONFIGURED',
 category text NOT NULL, evidence_id uuid NOT NULL REFERENCES raw_evidence(id), updated_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(source_id, document_id)
);
CREATE TABLE event_evidence (
 event_id uuid REFERENCES financial_event(id), evidence_id uuid REFERENCES raw_evidence(id), PRIMARY KEY(event_id,evidence_id)
);
CREATE TABLE ingestion_quarantine (
 id uuid PRIMARY KEY, job_id uuid NOT NULL REFERENCES ingestion_job(id), reason text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE stream_outbox (
 id bigserial PRIMARY KEY, event_id uuid NOT NULL REFERENCES financial_event(id), evidence_id uuid NOT NULL REFERENCES raw_evidence(id),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(event_id,evidence_id)
);
CREATE INDEX outbox_created_idx ON stream_outbox(created_at);
CREATE INDEX evidence_source_time_idx ON raw_evidence(source_id,ingested_at DESC);
CREATE TABLE audit_log (id bigserial PRIMARY KEY, actor text NOT NULL, action text NOT NULL, target text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE source_health_history (id bigserial PRIMARY KEY, source_id text REFERENCES source_registry(id), job_id uuid REFERENCES ingestion_job(id), status text NOT NULL, created_at timestamptz NOT NULL DEFAULT now());
-- Only source configuration is seeded; no financial events or market observations.
INSERT INTO source_registry VALUES
 ('FED','美联储官方新闻','L0','https://www.federalreserve.gov/feeds/press_all.xml',false,300),
 ('SEC','SEC 公司申报','L0','https://data.sec.gov/submissions/',false,300),
 ('GDELT','全球事件发现','L2','https://www.gdeltproject.org/data.html',false,900),
 ('FRED','FRED 宏观指标','L0','https://api.stlouisfed.org/fred/series/observations',false,3600);
INSERT INTO source_license(source_id,terms_url) VALUES
 ('FED','https://www.federalreserve.gov/website-linking-policies.htm'),
 ('SEC','https://www.sec.gov/about/privacy-information'),
 ('GDELT','https://www.gdeltproject.org/data.html'),
 ('FRED','https://fred.stlouisfed.org/docs/api/terms_of_use.html');
