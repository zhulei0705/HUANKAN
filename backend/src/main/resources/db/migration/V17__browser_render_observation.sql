-- Existing deliveries remain unchanged; new browser observations are append-only.
ALTER TABLE stream_delivery ADD COLUMN delivery_type text CHECK(delivery_type IN('LIVE','REPLAY','BACKFILL'));
CREATE TABLE browser_delivery_observation(
 delivery_id uuid PRIMARY KEY REFERENCES stream_delivery(id),
 connection_id uuid NOT NULL REFERENCES stream_connection(id),
 event_id uuid NOT NULL REFERENCES financial_event(id),
 received_at timestamptz NOT NULL,rendered_at timestamptz,
 received_monotonic_ms double precision NOT NULL CHECK(received_monotonic_ms>=0),
 render_monotonic_ms double precision,
 acknowledged_at timestamptz NOT NULL DEFAULT clock_timestamp(),
 clock_offset_ms double precision,clock_uncertainty_ms double precision,
 CHECK(render_monotonic_ms IS NULL OR render_monotonic_ms>=received_monotonic_ms),
 CHECK((rendered_at IS NULL)=(render_monotonic_ms IS NULL)),
 CHECK(clock_uncertainty_ms IS NULL OR clock_uncertainty_ms>=0));
CREATE INDEX browser_live_event ON browser_delivery_observation(event_id,acknowledged_at);
CREATE FUNCTION immutable_browser_observation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Browser receipt is immutable'; END $$;
CREATE TRIGGER browser_observation_immutable BEFORE UPDATE OR DELETE ON browser_delivery_observation FOR EACH ROW EXECUTE FUNCTION immutable_browser_observation();
