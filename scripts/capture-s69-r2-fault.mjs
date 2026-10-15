import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {sql} from './s46-db.mjs';
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),prefix=`docs/evidence/s69-r2-fault-${stamp}`;
// Only sanitized statement structure reaches the evidence file; never emit driver SQL or values.
function sanitized(query){return query.replace(/\/\*[\s\S]*?(?:\*\/|$)/g,'[注释已脱敏]').replace(/--[^\r\n]*/g,'[注释已脱敏]').replace(/(\$[A-Za-z0-9_]*\$)[\s\S]*?\1/g,'[文本已脱敏]').replace(/'(?:''|[^'])*(?:'|$)/g,"'[数据已脱敏]'").replace(/"(?:""|[^"])*(?:"|$)/g,'[引用标识已脱敏]').replace(/\b\d+(?:\.\d+)?\b/g,'?');}
const snapshot=sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('at',clock_timestamp(),'started_at',pg_postmaster_start_time(),'activity',(SELECT json_agg(t) FROM(SELECT pid,client_port,state,backend_type,xact_start,query_start,wait_event_type,wait_event,pg_blocking_pids(pid) blockers,query FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid() AND state<>'idle' ORDER BY pid)t),'running_jobs',(SELECT json_agg(t) FROM(SELECT source_id,status,started_at,heartbeat_at FROM ingestion_job WHERE status='RUNNING' ORDER BY started_at)t)); COMMIT;`);
for(const row of snapshot.activity||[]){row.query_sha256=crypto.createHash('sha256').update(row.query||'').digest('hex');row.sanitized_sql=sanitized(row.query||'');delete row.query;}
snapshot.sockets=JSON.parse(execFileSync('powershell.exe',['-NoProfile','-Command',"@(Get-NetTCPConnection -RemotePort 5432 -ErrorAction SilentlyContinue|Select-Object LocalPort,OwningProcess)|ConvertTo-Json -Compress"],{encoding:'utf8',timeout:5000}));
for(const row of snapshot.activity||[])row.windows_pid=snapshot.sockets.find(s=>s.LocalPort===row.client_port)?.OwningProcess??null;
snapshot.scope='故障期间只读状态；SQL仅脱敏结构与SHA256，未取消连接、任务或修改数据库';
fs.copyFileSync('runtime/s70-main.log',prefix+'-java.log',fs.constants.COPYFILE_EXCL);
snapshot.java_log={file:prefix+'-java.log',sha256:crypto.createHash('sha256').update(fs.readFileSync(prefix+'-java.log')).digest('hex')};
fs.writeFileSync(prefix+'.json',JSON.stringify(snapshot,null,2),{flag:'wx'});
console.log(JSON.stringify({file:prefix+'.json',active:snapshot.activity?.length||0,owned_formal:snapshot.activity?.filter(x=>x.windows_pid===43884).length||0}));
