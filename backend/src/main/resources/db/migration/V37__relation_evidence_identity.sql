CREATE UNIQUE INDEX relation_evidence_identity ON entity_relation(subject_id,object_id,type,evidence_id) WHERE evidence_id IS NOT NULL;
