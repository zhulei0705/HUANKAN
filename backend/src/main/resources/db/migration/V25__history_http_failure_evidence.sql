ALTER TABLE macro_history_request ADD COLUMN raw_response bytea;
ALTER TABLE macro_history_request ADD CONSTRAINT history_response_hash CHECK(raw_response IS NULL OR sha256=encode(sha256(raw_response),'hex'));
