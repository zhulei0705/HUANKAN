-- Append-only research ranking, independent of notification qualification.
CREATE TABLE research_priority_snapshot(id uuid PRIMARY KEY,owner_key char(64) NOT NULL,as_of timestamptz NOT NULL,rule_version text NOT NULL,filters jsonb NOT NULL,inputs jsonb NOT NULL,input_hash char(64) NOT NULL,results jsonb NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE INDEX research_priority_owner ON research_priority_snapshot(owner_key,created_at DESC);
CREATE TRIGGER research_priority_immutable BEFORE UPDATE OR DELETE ON research_priority_snapshot FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
