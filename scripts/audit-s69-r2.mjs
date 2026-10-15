import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {sql} from './s46-db.mjs';
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),file=`docs/evidence/s69-r2-baseline-${stamp}.json`;
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,'')),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const index=read('docs/evidence/s69-r1-final-index-20261013T115021.json'),delivery=read('docs/evidence/s69-r1-delivery-20261013T120843.json');
const frozen=[...index.manifest,...delivery.manifest].map(x=>({path:x.snapshot,expected:x.sha256,actual:hash(x.snapshot),match:hash(x.snapshot)===x.sha256}));
const migrations=read('docs/evidence/s62-historical-file-manifest.json').files.filter(x=>x.path.startsWith('backend/src/main/resources/db/migration/')).map(x=>({path:x.path,match:hash(x.path)===x.sha256}));
const database=sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='5s';
SELECT json_build_object('at',clock_timestamp(),'database',current_database(),'port',inet_server_port(),'started_at',pg_postmaster_start_time(),'max_connections',current_setting('max_connections'),'reserved',current_setting('superuser_reserved_connections'),'idle_session_timeout',current_setting('idle_session_timeout'),
'activity',(SELECT coalesce(json_agg(t),'[]'::json) FROM(SELECT pid,datname,application_name,backend_start,xact_start,query_start,state,wait_event_type,wait_event,round(extract(epoch from clock_timestamp()-xact_start)::numeric,3) transaction_age_seconds,round(extract(epoch from clock_timestamp()-query_start)::numeric,3) query_age_seconds FROM pg_stat_activity WHERE backend_type='client backend' AND pid<>pg_backend_pid() ORDER BY pid)t),
'locks',(SELECT count(*) FROM pg_locks WHERE NOT granted),'migrations',(SELECT json_agg(t) FROM(SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank)t),
'fixture',(SELECT count(*) FROM raw_evidence WHERE source_document_id ILIKE '%fixture%' OR source_document_id ILIKE 'isolated-%' OR source_document_id ILIKE 'r1-isolated-%'),
'backlog',(SELECT greatest(coalesce((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT'),
'raw_count',(SELECT count(*) FROM raw_evidence),'integrity_failed',(SELECT count(*) FROM evidence_integrity_check WHERE NOT passed),
'source_status',(SELECT json_agg(t) FROM(SELECT id,enabled,level FROM source_registry ORDER BY id)t),
'gdelt_verification',(SELECT json_agg(t) FROM(SELECT verification_status,count(*) FROM raw_evidence WHERE source_id='GDELT' GROUP BY verification_status)t),
'recent_jobs',(SELECT coalesce(json_agg(t),'[]'::json) FROM(SELECT source_id,status,started_at,finished_at,duration_ms,error_code FROM ingestion_job ORDER BY started_at DESC LIMIT 20)t)); COMMIT;`);
const prior=read('docs/evidence/s66-final-audit.json');
const health=await fetch('http://127.0.0.1:18080/actuator/health',{signal:AbortSignal.timeout(5000)});
const status=await fetch('http://127.0.0.1:18080/api/stream/browser/status',{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(5000)});
const report={at:new Date().toISOString(),scope:'正式实例只读低负载快照，未获取SQL文本；不是历史连接持有者还原或新观察窗口',windows:index.original_windows,verdicts:index.verdicts,frozen,migration_files:migrations,migration_changes:prior.migrations.filter(m=>!database.migrations.some(n=>m.version===n.version&&m.checksum===n.checksum&&n.success)),formal_jar_sha256:hash('backend/target/huankan-s70-reviewed-0.1.0.jar'),r1_jar_sha256:hash('backend/target/huankan-s69-r1-reviewed-final-0.1.0.jar'),git_status:execFileSync('git',['status','--short'],{encoding:'utf8'}),database,health:{http:health.status,body:await health.json()},sse:{http:status.status,body:await status.json()},historical_jdbc_lines:fs.readFileSync('runtime/s70-main.log','utf8').split(/\r?\n/).filter(l=>l.includes('SQLTransientConnectionException: HikariPool'))};
fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});
console.log(JSON.stringify({file,frozen:frozen.length,mismatches:frozen.filter(x=>!x.match).length,migration_changes:report.migration_changes.length,migration_file_changes:migrations.filter(x=>!x.match).length,health:report.health,live:report.sse.body.two_client_samples,connections:database.activity.length,waiting_locks:database.locks,fixture:database.fixture,backlog:database.backlog}));
