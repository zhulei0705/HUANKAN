-- Each task is reconciled against its own immutable evidence, without scanning all history.
CREATE INDEX raw_evidence_job_audit ON raw_evidence(job_id,id);
