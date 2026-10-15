BEGIN;
UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,
 terms_url='https://www.gdeltproject.org/data.html',
 review_basis='Official GDELT open raw dataset documentation checked 2026-10-09. Local research GKG metadata only. Underlying article copyrights not granted; do not download or republish article text. Aggregated leads always PENDING; processing time is not publication time.',
 usage_scope='LOCAL_GKG_METADATA_RESEARCH_ONLY',reviewed_at=now(),valid_until=now()+interval '90 days' WHERE source_id='GDELT';
UPDATE source_registry SET enabled=true,endpoint='https://data.gdeltproject.org/gdeltv2/lastupdate.txt' WHERE id='GDELT';
COMMIT;
