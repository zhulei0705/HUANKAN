ALTER TABLE entity_relation DROP CONSTRAINT entity_relation_type_check;
ALTER TABLE entity_relation ADD CONSTRAINT entity_relation_type_check CHECK(type IN('ISSUER','LISTED','INDUSTRY','BUSINESS','CUSTOMER','SUPPLIER','COMPETITOR','EQUITY','MATERIAL_DEPENDENCY','MACRO_SENSITIVITY','TRANSMISSION','PARTNER','PRODUCT','UPSTREAM','DOWNSTREAM','CAPEX','REGIONAL_REVENUE','ACCOUNTING_DIRECT_PARENT','ACCOUNTING_ULTIMATE_PARENT','LEGAL_IDENTITY'));
ALTER TABLE entity_relation DROP CONSTRAINT entity_relation_relation_kind_check;
ALTER TABLE entity_relation ADD CONSTRAINT entity_relation_relation_kind_check CHECK(relation_kind IN('BUSINESS_FACT','INDUSTRY_MEMBERSHIP','MACRO_HYPOTHESIS','AI_CANDIDATE','STATISTICAL','ACCOUNTING_CONSOLIDATION','LEGAL_IDENTITY'));
ALTER TABLE entity_relation DROP CONSTRAINT relation_kind_verified;
ALTER TABLE entity_relation ADD CONSTRAINT relation_kind_verified CHECK(status<>'VERIFIED' OR relation_kind IN('BUSINESS_FACT','INDUSTRY_MEMBERSHIP','ACCOUNTING_CONSOLIDATION','LEGAL_IDENTITY'));
CREATE TABLE relation_review_history(id bigserial PRIMARY KEY,relation_id uuid NOT NULL REFERENCES entity_relation(id),
 decision text NOT NULL CHECK(decision IN('ACCEPT','REJECT','RETURN_FOR_PROOF')),actor text NOT NULL,reason text NOT NULL,
 evidence_id uuid REFERENCES raw_evidence(id),excerpt text,limitations text NOT NULL,created_at timestamptz NOT NULL DEFAULT clock_timestamp());
CREATE TRIGGER relation_review_immutable BEFORE UPDATE OR DELETE ON relation_review_history FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
CREATE TABLE impact_assessment(id uuid PRIMARY KEY,fact_id uuid NOT NULL REFERENCES fact_candidate(id),rule_id text NOT NULL,rule_version text NOT NULL,
 as_of timestamptz NOT NULL,input_evidence_id uuid NOT NULL REFERENCES raw_evidence(id),input_hash char(64) NOT NULL,
 conditions jsonb NOT NULL,report jsonb NOT NULL,generated_by text NOT NULL DEFAULT 'RULE' CHECK(generated_by='RULE'),
 created_at timestamptz NOT NULL DEFAULT clock_timestamp(),UNIQUE(fact_id,rule_id,rule_version,as_of));
CREATE INDEX impact_assessment_page ON impact_assessment(created_at DESC,id);
CREATE TRIGGER impact_immutable BEFORE UPDATE OR DELETE ON impact_assessment FOR EACH ROW EXECUTE FUNCTION protect_raw_evidence();
