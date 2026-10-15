import fs from 'node:fs';
import crypto from 'node:crypto';
import {sql} from './s46-db.mjs';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,'');
const report={at:new Date().toISOString(),scope:'R4正式低负载只读；不执行历史SQL、不注入、不修改数据库参数',frozen:[],samples:[],metrics:[]};
for(const name of ['s69-r1-final-index-20261013T115021.json','s69-r1-delivery-20261013T120843.json','s69-r2-delivery-20261014T014238.json','s69-r3-delivery-20261014T041942.json']){
 const file='docs/evidence/'+name, index=read(file);const checks=index.manifest.map(x=>({file:x.snapshot,expected:x.sha256,actual:fs.existsSync(x.snapshot)?hash(x.snapshot):null}));
 report.frozen.push({file,sha256:hash(file),count:checks.length,mismatches:checks.filter(x=>x.actual!==x.expected)});
}
report.jars=['backend/target/huankan-s70-reviewed-0.1.0.jar','backend/target/huankan-s69-r3-candidate-0.1.0.jar'].map(file=>({file,sha256:hash(file)}));
report.settings=sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('at',clock_timestamp(),'database',current_database(),'port',inet_server_port(),'user',current_user,'server_started',pg_postmaster_start_time(),'version',version(),'settings',(SELECT json_object_agg(name,setting) FROM pg_settings WHERE name IN ('max_connections','superuser_reserved_connections','reserved_connections','shared_preload_libraries','track_io_timing','log_min_duration_statement','log_lock_waits','logging_collector','log_directory','log_filename')),'roles',(SELECT json_agg(rolname) FROM pg_roles WHERE pg_has_role(current_user,oid,'MEMBER')),'extensions',(SELECT json_agg(extname) FROM pg_extension),'available_statements',(SELECT json_agg(t) FROM (SELECT name,default_version,installed_version FROM pg_available_extensions WHERE name='pg_stat_statements')t),'migrations',(SELECT json_agg(t) FROM (SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank)t)); COMMIT;`);
for(let i=0;i<12;i++){
 try{report.samples.push(sql(`BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('at',clock_timestamp(),'self',pg_backend_pid(),'activity',(SELECT json_agg(t) FROM (SELECT pid,datname,usename,application_name,client_addr,client_port,backend_start,xact_start,query_start,state,wait_event_type,wait_event,backend_type,extract(epoch FROM clock_timestamp()-xact_start) xact_seconds,CASE WHEN state='active' AND pid<>pg_backend_pid() THEN md5(query) END sql_fingerprint FROM pg_stat_activity ORDER BY pid)t),'waiting_locks',(SELECT count(*) FROM pg_locks WHERE NOT granted),'database_stats',(SELECT row_to_json(t) FROM (SELECT numbackends,xact_commit,xact_rollback,blks_read,blks_hit,blk_read_time,blk_write_time,temp_files,temp_bytes,deadlocks,stats_reset FROM pg_stat_database WHERE datname=current_database())t),'io',(SELECT json_agg(t) FROM pg_stat_io t)); COMMIT;`));}catch(e){report.samples.push({at:new Date().toISOString(),error:String(e.message).slice(0,600)});}
 if(i<11)await new Promise(r=>setTimeout(r,5000));
}
for(const name of ['hikaricp.connections.active','hikaricp.connections.idle','hikaricp.connections.pending','hikaricp.connections.max','hikaricp.connections.acquire','hikaricp.connections.timeout','process.cpu.usage','jvm.memory.used']){
 try{const r=await fetch('http://127.0.0.1:18080/actuator/metrics/'+name,{headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN},signal:AbortSignal.timeout(4000)});report.metrics.push({name,http:r.status,body:r.ok?await r.json():null});}catch(e){report.metrics.push({name,error:String(e)})}
}
try{const r=await fetch('http://127.0.0.1:18080/actuator/health',{signal:AbortSignal.timeout(5000)});report.health={http:r.status,body:await r.json()};}catch(e){report.health={error:String(e)}}
const file=`docs/evidence/s69-r4-capacity-${stamp}.json`;fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});
console.log(JSON.stringify({file,frozen:report.frozen,settings:report.settings,samples:report.samples.map(s=>({at:s.at,error:s.error,clients:s.activity?.filter(a=>a.backend_type==='client backend'&&a.pid!==s.self).length,active:s.activity?.filter(a=>a.state==='active'&&a.pid!==s.self).length,waiting:s.waiting_locks})),metrics:report.metrics,health:report.health},null,2));
