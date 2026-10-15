BEGIN;
UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,
 review_basis='2026-10-09 checked official RSS subscription guide and website terms. Local private free research reader only; preserve actual RSS response as restricted audit evidence, display Chinese title/time/source/link metadata only. Do not republish article bodies, signed articles, third-party works or grant redistribution rights.',
 usage_scope='LOCAL_NBS_RSS_METADATA_AND_RESTRICTED_AUDIT',reviewed_at=now(),valid_until=now()+interval '90 days' WHERE source_id='NBS';
UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,
 review_basis='2026-10-09 checked ECB copyright conditions and SDMX dataflow response. Local accurate official reference-rate observations with source attribution, free original access and explicit daily reference-rate label; no signed papers, real-time trading quote claims or paid redistribution.',
 usage_scope='LOCAL_ECB_SDMX_OFFICIAL_REFERENCE_OBSERVATIONS',reviewed_at=now(),valid_until=now()+interval '90 days' WHERE source_id='ECB_DATA';
UPDATE source_registry SET enabled=true WHERE id IN('NBS','ECB_DATA');
INSERT INTO audit_log(actor,action,target) VALUES('local-development-license-review','LIMITED_LICENSE_APPROVAL','NBS RSS metadata / ECB_DATA reference statistics');
COMMIT;
