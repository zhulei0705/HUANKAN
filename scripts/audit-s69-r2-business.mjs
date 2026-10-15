import fs from 'node:fs';import {sql} from './s46-db.mjs';
const record=sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SELECT json_build_object('at',clock_timestamp(),
'watch_hashes',(SELECT coalesce(json_agg(t),'[]'::json) FROM(SELECT id,encode(sha256(convert_to(row_to_json(w)::text,'UTF8')),'hex') row_sha256 FROM user_watch w ORDER BY id)t),
'outbox_duplicate_groups',(SELECT count(*) FROM(SELECT event_id,evidence_id,notification_kind FROM stream_outbox GROUP BY event_id,evidence_id,notification_kind HAVING count(*)>1)t),
'gdelt_verified',(SELECT count(*) FROM raw_evidence WHERE source_id='GDELT' AND verification_status='VERIFIED'),
'incident_jobs',(SELECT json_agg(t) FROM(SELECT source_id,status,started_at,finished_at,heartbeat_at,duration_ms,new_count,error_code FROM ingestion_job WHERE started_at BETWEEN '2026-10-14T08:40:00+08:00'::timestamptz AND '2026-10-14T09:00:00+08:00'::timestamptz ORDER BY started_at)t)); COMMIT;`);
const file='docs/evidence/s69-r2-business-'+Date.now()+'.json';fs.writeFileSync(file,JSON.stringify(record,null,2),{flag:'wx'});console.log(JSON.stringify({file,watches:record.watch_hashes.length,outbox_duplicates:record.outbox_duplicate_groups,gdelt_verified:record.gdelt_verified,jobs:record.incident_jobs?.length}));
