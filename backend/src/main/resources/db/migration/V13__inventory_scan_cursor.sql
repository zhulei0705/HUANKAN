ALTER TABLE source_cursor ADD COLUMN inventory_checked_until varchar(14);
INSERT INTO source_cursor(source_id,inventory_checked_until) SELECT 'GDELT',min(batch_id) FROM gdelt_batch HAVING min(batch_id) IS NOT NULL ON CONFLICT(source_id) DO UPDATE SET inventory_checked_until=excluded.inventory_checked_until;
