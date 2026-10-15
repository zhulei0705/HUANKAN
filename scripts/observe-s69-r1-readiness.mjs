import fs from 'node:fs';import {execFileSync} from 'node:child_process';
const seconds=Number(process.argv[2]||60);if(!Number.isInteger(seconds)||seconds<30||seconds>120)throw Error('这里只允许30—120秒隔离探针验证，不创建正式24小时窗口');
if(process.env.HUANKAN_TEST_DB_URL!=='jdbc:postgresql://127.0.0.1:55432/huankan_test')throw Error('隔离实例配置不匹配');
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),file=`docs/evidence/s69-r1-probe-${stamp}.json`;
const entry=JSON.parse(fs.readFileSync('runtime/s69-r1-current-test-process.json','utf8').replace(/^\uFEFF/,''));if(!/^r1_web_[a-f0-9]{32}$/.test(entry.schema))throw Error('隔离schema不匹配');
const start=process.hrtime.bigint(),report={started_at:new Date().toISOString(),requested_seconds:seconds,qualification:'隔离短时测步骤耗时与固定目标调度；不是新正式观察窗口或24小时稳定性证据',schema:entry.schema,samples:[]};
async function phase(name,fn){const at=process.hrtime.bigint();try{const result=await fn();return {name,duration_ms:Number(process.hrtime.bigint()-at)/1e6,result};}catch(e){return {name,duration_ms:Number(process.hrtime.bigint()-at)/1e6,error:String(e)};}}
for(let i=0;Number(process.hrtime.bigint()-start)/1e9<seconds;i++){
 const target=i*5000,elapsed=Number(process.hrtime.bigint()-start)/1e6;await new Promise(r=>setTimeout(r,Math.max(0,target-elapsed)));
 const at=new Date().toISOString(),begin=process.hrtime.bigint();
 const phases=await Promise.all([
  phase('健康HTTP',async()=>{const r=await fetch('http://127.0.0.1:18088/actuator/health',{signal:AbortSignal.timeout(2000)});const body=await r.json();if(r.status!==200||body.status!=='UP')throw Error('健康状态不正常');return {http:r.status,body};}),
  phase('SSE状态HTTP',async()=>{const r=await fetch('http://127.0.0.1:18088/api/stream/browser/status',{method:'POST',headers:{Authorization:'Bearer isolated-s69-r1-test-token-32-characters','Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(2000)});if(!r.ok)throw Error('SSE状态接口失败');return {http:r.status,scope:'本次隔离schema，数据不计正式LIVE'};}),
  phase('隔离SQL',async()=>JSON.parse(execFileSync('D:/Program Files/PostgreSQL/18/bin/psql.exe',['-h','127.0.0.1','-p','55432','-U',process.env.HUANKAN_TEST_DB_USER,'-d','huankan_test','-w','-Atq','-v','ON_ERROR_STOP=1','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_TEST_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:`BEGIN READ ONLY; SET LOCAL statement_timeout='2s'; SET LOCAL search_path TO ${entry.schema}; SELECT json_build_object('database',current_database(),'port',inet_server_port(),'database_time',clock_timestamp(),'audit_backlog',(SELECT greatest(coalesce((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT')); COMMIT;`,encoding:'utf8',timeout:3000})))
 ]);
 const duration_ms=Number(process.hrtime.bigint()-begin)/1e6;
 report.samples.push({at,target_elapsed_ms:target,actual_elapsed_ms:Number(begin-start)/1e6,scheduling_lag_ms:Number(begin-start)/1e6-target,duration_ms,phases});fs.writeFileSync(file,JSON.stringify(report,null,2));
}
report.finished_at=new Date().toISOString();report.passed=report.samples.length>=6&&report.samples.every(s=>s.duration_ms<5000&&s.phases.every(p=>!p.error&&(!p.result?.database||p.result.database==='huankan_test'&&p.result.port===55432&&p.result.audit_backlog===0)));
fs.writeFileSync(file,JSON.stringify(report,null,2));console.log(JSON.stringify({file,passed:report.passed,samples:report.samples.length,max_round_ms:Math.max(...report.samples.map(s=>s.duration_ms))}));if(!report.passed)process.exitCode=1;
