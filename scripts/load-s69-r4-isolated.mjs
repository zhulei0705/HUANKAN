import fs from 'node:fs';import {execFileSync} from 'node:child_process';
const entry=JSON.parse(fs.readFileSync('runtime/s69-r4-test-process.json','utf8').replace(/^\uFEFF/,''));
if(!/^r1_web_[a-f0-9]{32}$/.test(entry.schema)||entry.database.port!==55432||entry.database.database!=='huankan_test')throw Error('隔离身份不符');
const base='http://127.0.0.1:18088',headers={Authorization:'Bearer isolated-s69-r1-test-token-32-characters','Content-Type':'application/json'};
const calls=['events/query','practical/priority/overview','macro-changes/catalog','transmission/catalog','stream/cursor'];
const report={at:new Date().toISOString(),schema:entry.schema,database:entry.database,scope:'隔离HTTP有界突发；小数据、采集关闭，非生产等效；真实采集/治理并发由完整后端测试单列',stages:[],metrics:[]};
async function metric(name){const r=await fetch(base+'/actuator/metrics/'+name,{headers,signal:AbortSignal.timeout(3000)});return {name,http:r.status,body:r.ok?await r.json():null};}
for(const concurrency of [1,4,8,12]){
 const rows=[],start=performance.now();let next=0;
 await Promise.all(Array.from({length:concurrency},async()=>{while(next<24){const index=next++,path=calls[index%calls.length],t=performance.now();try{const r=await fetch(base+'/api/'+path,{method:'POST',headers,body:JSON.stringify({page:index%3}),signal:AbortSignal.timeout(35000)});await r.arrayBuffer();rows.push({path,http:r.status,ms:performance.now()-t});}catch(e){rows.push({path,error:String(e),ms:performance.now()-t});}}}));
 const elapsed=performance.now()-start,sorted=rows.map(r=>r.ms).sort((a,b)=>a-b);
 report.stages.push({concurrency,requests:rows.length,elapsed_ms:elapsed,requests_per_second:rows.length*1000/elapsed,p50_ms:sorted[Math.floor(sorted.length*.5)],p95_ms:sorted[Math.floor(sorted.length*.95)],max_ms:sorted.at(-1),unexpected_failures:rows.filter(r=>r.http!==200).length,rows});
 report.metrics.push({concurrency,at:new Date().toISOString(),values:await Promise.all(['hikaricp.connections.active','hikaricp.connections.pending','hikaricp.connections.acquire','hikaricp.connections.timeout','huankan.jdbc.query','huankan.jdbc.transaction','process.cpu.usage','jvm.memory.used'].map(metric))});
}
report.passed=report.stages.every(s=>s.unexpected_failures===0);
const file='docs/evidence/s69-r4-http-load-'+Date.now()+'.json';fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});console.log(JSON.stringify({file,passed:report.passed,stages:report.stages.map(({rows,...s})=>s)},null,2));
