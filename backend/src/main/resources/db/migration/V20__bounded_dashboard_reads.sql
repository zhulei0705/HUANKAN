CREATE INDEX raw_evidence_verification_count ON raw_evidence(verification_status);
CREATE INDEX financial_event_source_count ON financial_event(source_id);
CREATE INDEX event_candidate_verification_count ON event_candidate(verification);
CREATE INDEX ingestion_job_status_count ON ingestion_job(status);
