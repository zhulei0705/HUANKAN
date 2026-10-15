import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync,statSync,readdirSync} from 'node:fs';
import {join} from 'node:path';
const duration=Number(process.argv[2]||180),interval=15;
const output=process.argv[3]||'docs/evidence/s13-runtime-observation.json';
if(!/^docs\/evidence\/[a-z0-9-]+[.]json$/.test(output))throw new Error('观测报告路径无效');
if(duration<30||duration>3600)throw new Error('观测时长必须为30至3600秒');
const sql=q=>JSON.parse(execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','5432','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-At','-c',q],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD},encoding:'utf8'}));
function bytes(path){let n=0;try{const initial=statSync(path);if(!initial.isDirectory())return initial.size;for(const name of readdirSync(path)){const file=join(path,name),s=statSync(file);n+=s.isDirectory()?bytes(file):s.size;}}catch{}return n;}
const started=Date.now(),samples=[];
do{
 const pid=JSON.parse(readFileSync('runtime/backend-process.json','utf8').replace(/^\uFEFF/,'')).processId;
 const process=JSON.parse(execFileSync('powershell.exe',['-NoProfile','-Command',`Get-Process -Id ${Number(pid)} | Select-Object Id,WorkingSet64,PrivateMemorySize64,CPU,HandleCount,Threads | ConvertTo-Json -Depth 1`],{encoding:'utf8'}));
 const database=sql("SELECT json_build_object('checked_at',now(),'db_connections',(SELECT count(*) FROM pg_stat_activity WHERE datname='huankan'),'running_jobs',(SELECT count(*) FROM ingestion_job WHERE status='RUNNING'),'failed_jobs',(SELECT count(*) FROM ingestion_job WHERE status='FAILED'),'http_errors',(SELECT count(*) FROM http_request_observation WHERE error IS NOT NULL OR status>=400),'evidence',(SELECT count(*) FROM raw_evidence),'database_bytes',pg_database_size(current_database()),'outbox_unattempted_new',(SELECT count(*) FROM stream_outbox o WHERE notification_kind='NEW_EVENT' AND NOT EXISTS(SELECT 1 FROM stream_delivery d WHERE d.outbox_id=o.id AND outcome='SENT')),'sse_connections',(SELECT count(*) FROM stream_connection WHERE closed_at IS NULL AND last_activity_at>now()-interval '10 seconds'),'leases',(SELECT COALESCE(json_agg(t),'[]'::json) FROM collector_lease t));");
 samples.push({time:new Date().toISOString(),process,database,log_bytes:bytes('runtime/backend.log')+bytes('runtime/backend-error.log'),download_bytes:bytes('runtime/gdelt-downloads')});
 writeFileSync(output,JSON.stringify({started_at:new Date(started).toISOString(),actual_duration_seconds:(Date.now()-started)/1000,requested_duration_seconds:duration,complete:false,samples},null,2));
 if(Date.now()-started>=duration*1000)break;await new Promise(r=>setTimeout(r,Math.min(interval*1000,duration*1000-(Date.now()-started))));
}while(true);
writeFileSync(output,JSON.stringify({started_at:new Date(started).toISOString(),finished_at:new Date().toISOString(),actual_duration_seconds:(Date.now()-started)/1000,requested_duration_seconds:duration,complete:true,samples},null,2));
console.log(JSON.stringify({duration_seconds:(Date.now()-started)/1000,samples:samples.length,memory_first:samples[0].process.WorkingSet64,memory_last:samples.at(-1).process.WorkingSet64}));
