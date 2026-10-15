CREATE TABLE http_request_observation (
 id bigserial PRIMARY KEY, source_id varchar(20) REFERENCES source_registry(id),
 endpoint text NOT NULL, attempted_at timestamptz NOT NULL, completed_at timestamptz NOT NULL,
 status integer, duration_ms bigint NOT NULL CHECK(duration_ms>=0),
 etag text, last_modified text, response_bytes bigint, error text
);
CREATE INDEX http_observation_source_time ON http_request_observation(source_id,attempted_at DESC);
CREATE TABLE stream_delivery (
 id uuid PRIMARY KEY, connection_id uuid NOT NULL, outbox_id bigint REFERENCES stream_outbox(id),
 sent_at timestamptz NOT NULL, outcome varchar(10) NOT NULL CHECK(outcome IN ('SENT','FAILED')),
 client_received_at timestamptz, acknowledged_at timestamptz
);
CREATE INDEX stream_delivery_outbox ON stream_delivery(outbox_id,sent_at);
ALTER TABLE source_registry ADD COLUMN region text NOT NULL DEFAULT '待配置';
UPDATE source_registry SET region='美国' WHERE id IN ('FED','SEC','FRED');
UPDATE source_registry SET region='全球' WHERE id='GDELT';
