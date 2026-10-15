-- Internal documented review for this local RSS reader, not regulatory or legal approval.
BEGIN;
UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,
 terms_url='https://www.federalreserve.gov/disclaimer.htm',
 review_basis='Board disclaimer Copyright/trademark: Board information is public domain unless indicated otherwise. RSS page explicitly supports readers/aggregators. Scope: local authenticated reader; store exact RSS metadata response and extracted Board titles/GUID/official links/times; attribute Board. Exclude third-party works, downloaded article bodies, photos, seals/logos, iframe framing, paid data and unrelated redistribution.',
 reviewed_at=now(),valid_until=now()+interval '90 days',usage_scope='LOCAL_BOARD_RSS_METADATA_ONLY'
 WHERE source_id='FED';
UPDATE source_registry SET enabled=true WHERE id='FED';
INSERT INTO audit_log(actor,action,target) VALUES('internal-source-review','FED_LOCAL_RSS_LICENSE_REVIEW','FED');
COMMIT;
