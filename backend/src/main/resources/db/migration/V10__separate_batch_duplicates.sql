ALTER TABLE ingestion_job ADD COLUMN duplicate_batch_count integer NOT NULL DEFAULT 0 CHECK(duplicate_batch_count>=0);
-- Older GDELT no-op jobs counted a whole batch in the record duplicate field. Correct the unit explicitly.
UPDATE ingestion_job SET duplicate_batch_count=duplicate_count,duplicate_count=0
WHERE source_id='GDELT' AND fetched_count=0 AND new_count=0 AND duplicate_count>0;
UPDATE gdelt_batch SET completed_lines=total_lines WHERE status='SUCCESS' AND total_lines IS NOT NULL;
ALTER TABLE gdelt_batch ADD CONSTRAINT gdelt_completed_payload CHECK(status NOT IN('PROCESSING','SUCCESS') OR raw_zip IS NOT NULL);
