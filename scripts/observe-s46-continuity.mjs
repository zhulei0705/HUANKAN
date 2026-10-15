import {readFileSync,writeFileSync,existsSync,statSync,readdirSync} from 'node:fs';
import {execFileSync} from 'node:child_process';
import {availableParallelism} from 'node:os';
import {sql} from './s46-db.mjs';
const seconds=Number(process.argv[2]||86400),output=process.argv[3]||'docs/evidence/s46-continuity.json';
if(!Number.isInteger(seconds)||seconds<60||seconds>86400||!/^docs\/evidence\/s46-[a-z0-9-]+\.json$/.test(output)||existsSync(output))throw Error('观察时长或独立输出路径无效');
const identity=JSON.parse(readFileSync('runtime/backend-process.json','utf8'));
const report={started_at:new Date().toISOString(),requested_seconds:seconds,backend:identity,samples:[],interruptions:[],status:'观察中',qualification:'未完成24小时验收'};
const started=Date.now();let previous=started,lastCpu,lastCpuAt;
function bytes(path,depth=0){let total=0;for(const entry of readdirSync(path,{withFileTypes:true})){const p=path+'/'+entry.name;try{total+=entry.isDirectory()?(depth<2?bytes(p,depth+1):0):statSync(p).size;}catch{}}return total;}
function save(){report.observed_seconds=Math.floor((Date.now()-started)/1000);writeFileSync(output,JSON.stringify(report,null,2));}
while(Date.now()-started<seconds*1000){
 const time=Date.now(),sample={at:new Date(time).toISOString()};
 if(time-previous>90000)report.interruptions.push({at:sample.at,reason:'采样间隔超过90秒'});previous=time;
 try{
  const now=JSON.parse(readFileSync('runtime/backend-process.json','utf8'));if(now.processId!==identity.processId||now.createdAt!==identity.createdAt)throw Error('后端进程已重启');
  const health=await fetch('http://127.0.0.1:18080/actuator/health',{signal:AbortSignal.timeout(10000)});sample.health_http=health.status;sample.health=await health.json();if(!health.ok||sample.health.status!=='UP')throw Error('健康检查失败');
  sample.process=JSON.parse(execFileSync('powershell.exe',['-NoProfile','-Command',`$p=Get-Process -Id ${identity.processId} -ErrorAction Stop; @{working_set=$p.WorkingSet64;private_bytes=$p.PrivateMemorySize64;cpu_seconds=$p.CPU;created_at=$p.StartTime.ToUniversalTime().ToString('o')}|ConvertTo-Json -Compress`],{encoding:'utf8'}));
  if(new Date(sample.process.created_at).getTime()!==new Date(identity.createdAt).getTime())throw Error('进程身份不匹配');
  if(lastCpu!==undefined)sample.process.cpu_percent=(sample.process.cpu_seconds-lastCpu)/((time-lastCpuAt)/1000)/availableParallelism()*100;lastCpu=sample.process.cpu_seconds;lastCpuAt=time;
  sample.storage={runtime_shallow_bytes:bytes('runtime'),coverage:'运行日志与三级以内文件；完整业务库增长另以pg_database_size计量；不逐分钟遍历冻结证据备份目录'};
  const stream=await fetch('http://127.0.0.1:18080/api/stream/browser/status',{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(15000)});sample.sse={http:stream.status,body:await stream.json()};
  sample.database=sql(`SELECT json_build_object('raw', (SELECT count(*) FROM raw_evidence),'outbox',(SELECT count(*) FROM stream_outbox),'audit_backlog',(SELECT greatest(COALESCE((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT'),'running_jobs',(SELECT count(*) FROM ingestion_job WHERE status='RUNNING'),'failed_jobs',(SELECT count(*) FROM ingestion_job WHERE status='FAILED'),'recent_failures',(SELECT count(*) FROM ingestion_job WHERE status='FAILED' AND started_at>now()-interval '1 minute'),'important_eligible',(SELECT count(*) FROM notification_qualification WHERE important_alert_eligible),'database_bytes',pg_database_size(current_database()),'connections',(SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()),'source_last_job',(SELECT json_agg(t) FROM(SELECT DISTINCT ON(source_id) source_id,status,started_at FROM ingestion_job ORDER BY source_id,started_at DESC)t));`);
 }catch(e){sample.error=e.message;report.interruptions.push({at:sample.at,reason:e.message});}
 report.samples.push(sample);save();await new Promise(resolve=>setTimeout(resolve,Math.min(60000,Math.max(0,seconds*1000-(Date.now()-started)))));
}
report.finished_at=new Date().toISOString();report.status=report.interruptions.length?'存在中断，不能验收':seconds>=86400?'24小时采样完成，需结合来源调度与浏览器日志审计':'短时采样完成';save();console.log(JSON.stringify({status:report.status,observed_seconds:report.observed_seconds,interruptions:report.interruptions.length}));
