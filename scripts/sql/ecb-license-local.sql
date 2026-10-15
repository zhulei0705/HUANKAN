BEGIN;
UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,
 terms_url='https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html',
 review_basis='ECB official RSS directory and copyright conditions checked 2026-10-09. Local free RSS metadata reader; accurate source attribution and full-window original links. No signed papers, article full text, logos, or paid redistribution.',
 usage_scope='LOCAL_ECB_RSS_METADATA_ONLY',reviewed_at=now(),valid_until=now()+interval '90 days' WHERE source_id='ECB';
UPDATE source_registry SET enabled=true WHERE id='ECB';
COMMIT;
