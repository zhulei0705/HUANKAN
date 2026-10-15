import fs from 'node:fs';import {sql} from './s46-db.mjs';
const dir='docs/evidence/s69-inc01/20261014T063813Z';const report={at:new Date().toISOString(),queries:[]};process.env.PGCONNECT_TIMEOUT='8';
if(process.env.HUANKAN_DB_URL!=='jdbc:postgresql://127.0.0.1:5432/huankan')throw Error('拒绝目标不匹配');
const queries={
 outbox_tail:`SELECT row_to_json(t) FROM (SELECT id,event_id,evidence_id,created_at FROM stream_outbox ORDER BY id DESC LIMIT 1)t`,
 cursors:`SELECT coalesce(json_agg(t),'[]') FROM notification_audit_cursor t`,
 audit_pending:`SELECT json_build_object('pending',count(*),'first',min(id),'last',max(id)) FROM stream_outbox WHERE id>(SELECT last_outbox FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT')`,
 integrity_tail:`SELECT json_agg(t) FROM (SELECT * FROM evidence_integrity_check ORDER BY checked_at DESC LIMIT 25)t`,
 integrity_failed:`SELECT count(*) FROM evidence_integrity_check WHERE NOT passed`,
 latest_evidence:`SELECT row_to_json(t) FROM (SELECT id,source_id,job_id,ingested_at,raw_content_hash FROM raw_evidence ORDER BY ingested_at DESC LIMIT 1)t`,
 delivery_tail:`SELECT row_to_json(t) FROM (SELECT outbox_id,sent_at,outcome,client_received_at,acknowledged_at FROM stream_delivery ORDER BY sent_at DESC LIMIT 1)t`,
 leases:`SELECT coalesce(json_agg(t),'[]') FROM collector_lease t`,
 unfinished_evidence:`SELECT json_build_object('job',(SELECT row_to_json(t) FROM(SELECT id,source_id,status,started_at,finished_at,heartbeat_at,lease_owner FROM ingestion_job WHERE id='987eaebe-b88d-41f9-a3b2-4f50eaea6eee')t),'persisted_evidence',(SELECT count(*) FROM raw_evidence WHERE job_id='987eaebe-b88d-41f9-a3b2-4f50eaea6eee'))`,
 gdelt_processing:`SELECT json_agg(t) FROM (SELECT * FROM gdelt_batch WHERE status='PROCESSING' LIMIT 5)t`,
 estimates:`SELECT json_agg(t) FROM(SELECT relname,n_live_tup FROM pg_stat_user_tables WHERE relname IN('raw_evidence','financial_event','stream_outbox'))t`
};
for(const [name,q] of Object.entries(queries)){const at=new Date().toISOString();try{report.queries.push({name,at,sql:q,result:sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SET LOCAL lock_timeout='1s'; ${q}; COMMIT;`)});}catch(e){report.queries.push({name,at,sql:q,error:String(e.message).slice(0,1200)});}}
fs.writeFileSync(`${dir}/database-supplement.json`,JSON.stringify(report,null,2),{flag:'wx'});
console.log(JSON.stringify(report.queries.map(x=>({...x,sql:undefined,result:x.name==='integrity_tail'?{count:x.result?.length,latest:x.result?.[0]}:x.result})),null,2));
