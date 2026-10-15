import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {sql} from './s46-db.mjs';
const root=path.resolve(import.meta.dirname,'..'),candidate=JSON.parse(fs.readFileSync(path.join(root,process.argv[2]||'runtime/s69-isolation-candidate.json')));
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),prefix=path.join(root,'docs/evidence',`s69-isolation-test-${stamp}`),base='http://127.0.0.1:5182';
const {chromium}=await import(pathToFileURL(path.join(candidate.work,'node_modules/playwright/index.mjs')).href);
const manifest=JSON.parse(fs.readFileSync(path.join(candidate.release,'manifest.json'))),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const results=[],trace=[],browsers=[],pages=[],pageErrors=[],requests=[[],[]];
const snapshot=()=>manifest.files.map(x=>({path:x.path,sha256:hash(path.join(candidate.release,x.path))}));
const before=snapshot();
const backlog=()=>sql("BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SELECT json_build_object('at',clock_timestamp(),'backlog',(SELECT greatest(coalesce((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT'),'postgres_started_at',pg_postmaster_start_time()); COMMIT;");
const databaseBefore=backlog();
async function until(fn,timeout=20000){const start=Date.now();let last;while(Date.now()-start<timeout){try{const value=await fn();if(value)return value;}catch(e){last=e;}await new Promise(r=>setTimeout(r,250));}throw last||Error('等待实际状态超时');}
async function check(name,fn){const start=new Date().toISOString();try{const detail=await fn();results.push({name,start,finish:new Date().toISOString(),status:'通过',detail});console.log(name+'：通过');}catch(e){results.push({name,start,finish:new Date().toISOString(),status:'失败',error:String(e)});console.log(name+'：失败 '+e.message);}}
async function healthyPages(){for(const page of pages){await until(async()=>await page.locator('.connection').innerText().then(t=>t.includes('已连接')));assert(await page.locator('[data-event-id]').count()>0);await page.locator('[data-event-id]').first().scrollIntoViewIfNeeded();assert(await page.locator('[data-event-id]').first().evaluate(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&r.top<innerHeight&&r.bottom>0;}));}assert.deepEqual(pageErrors,[]);}
async function servedHashes(){const values=[];for(const item of manifest.files.filter(x=>x.path.startsWith('public/'))){const response=await fetch(base+'/'+item.path.slice(7));assert.equal(response.status,200);const bytes=Buffer.from(await response.arrayBuffer());assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'),item.sha256);values.push(item.path);}return values;}
const app=path.join(candidate.work,'src/App.vue'),config=path.join(candidate.work,'vite.s69-isolated.config.ts'),appBytes=fs.readFileSync(app),configBytes=fs.readFileSync(config);
try{
 await check('双独立浏览器SSE连接与页面真实渲染',async()=>{
  for(let client=0;client<2;client++){
   const browser=await chromium.launch({headless:true});browsers.push(browser);const page=await browser.newPage({viewport:client?{width:390,height:844}:{width:1920,height:1080}});pages.push(page);
   page.on('pageerror',e=>pageErrors.push({client,at:new Date().toISOString(),message:e.message}));
   page.on('request',r=>{if(r.isNavigationRequest())requests[client].push(r.url());});
   page.on('response',r=>{if(new URL(r.url()).pathname==='/api/stream')trace.push({at:new Date().toISOString(),client,http:r.status(),connection_id:r.headers()['x-stream-connection-id'],cursor:r.request().headers()['last-event-id']});});
   await page.goto(base+'/#/live');await until(async()=>await page.locator('.connection').innerText().then(t=>t.includes('已连接')),60000);
   await until(async()=>await page.locator('[data-event-id]').count()>0,60000);
  }
  await healthyPages();assert(trace.some(x=>x.client===0&&x.http===200));assert(trace.some(x=>x.client===1&&x.http===200));assert.notEqual(trace.find(x=>x.client===0).connection_id,trace.find(x=>x.client===1).connection_id);return {browser_instances:2,engine:'Chromium',trace:trace.slice(),qualification:'隔离候选连通验证，不计正式官方LIVE'};
 });
 await check('开发重新构建不影响候选静态页面',async()=>{
  const npm=path.join(path.dirname(process.execPath),'node_modules/npm/bin/npm-cli.js');
  let stdout;try{stdout=execFileSync(process.execPath,[npm,'exec','--','vite','build','--config','vite.s69-isolated.config.ts'],{cwd:candidate.work,encoding:'utf8',maxBuffer:8*1024*1024});}catch(e){fs.writeFileSync(prefix+'-rebuild.log',(e.stdout||'')+(e.stderr||''),{flag:'wx'});throw e;}
  fs.writeFileSync(prefix+'-rebuild.log',stdout,{flag:'wx'});assert.deepEqual(snapshot(),before);await healthyPages();return {outDir:path.join(candidate.work,'dist-development'),http_resources:await servedHashes()};
 });
 await check('独立开发临时编译错误不影响候选静态页面',async()=>{
  const start=new Date().toISOString();
  try{fs.writeFileSync(app,Buffer.concat([appBytes,Buffer.from('\n<template>隔离测试临时重复模板</template>\n')]));const response=await until(async()=>{const r=await fetch('http://127.0.0.1:5181/src/App.vue');return r.status===500?r:null;});const failure=await response.text();fs.writeFileSync(prefix+'-intentional-compile-error.log',failure,{flag:'wx'});assert.deepEqual(snapshot(),before);await healthyPages();await servedHashes();return {start,fault_http:response.status,observation_http:(await fetch(base)).status,recovery:'finally恢复独立开发源码'};}finally{fs.writeFileSync(app,appBytes);await until(async()=>(await fetch('http://127.0.0.1:5181/src/App.vue')).status===200);}
 });
 await check('独立Vite配置热重启不影响候选静态页面',async()=>{
  const log=path.join(root,'runtime',`s69-isolation-${candidate.stamp}-development.log`),startSize=fs.statSync(log).size,nav=JSON.stringify(requests);
  try{fs.writeFileSync(config,Buffer.concat([configBytes,Buffer.from('\n// 隔离验证：触发开发Vite配置热重启\n')]));await until(()=>/server restarted|服务器.*重启/.test(fs.readFileSync(log).subarray(startSize).toString()),20000);await healthyPages();assert.equal(JSON.stringify(requests),nav);await servedHashes();return {vite_restart_log:fs.readFileSync(log).subarray(startSize).toString(),static_navigation_unchanged:true};}finally{fs.writeFileSync(config,configBytes);await until(async()=>(await fetch('http://127.0.0.1:5181/src/App.vue')).status===200);}
 });
 await check('正式候选前端资源SHA256保持稳定',async()=>{assert.deepEqual(snapshot(),before);assert.equal(hash(path.join(candidate.release,'manifest.json')),candidate.manifest_sha256);return {manifest_sha256:candidate.manifest_sha256,files:before,served:await servedHashes()};});
 await check('真实历史SSE补读与确认游标重连',async()=>{
  const event=sql("BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SELECT row_to_json(t) FROM(SELECT j.id,j.event_id,j.source_id FROM stream_journal j JOIN source_license l ON l.source_id=j.source_id WHERE j.official_eligible AND l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) ORDER BY j.id DESC LIMIT 1)t; COMMIT;");assert(event?.id,'需要真实官方历史记录');
  const read=async(cursor)=>pages[0].evaluate(async({cursor,source})=>{const stop=new AbortController(),timer=setTimeout(()=>stop.abort(),12000);let connectionId;try{const response=await fetch('/api/stream?scope=OFFICIAL&sources='+encodeURIComponent(source)+'&priority=0&watchOnly=false',{headers:{'Last-Event-ID':String(cursor)},signal:stop.signal});connectionId=response.headers.get('X-Stream-Connection-Id');const reader=response.body.getReader();let buffer='';const decoder=new TextDecoder();while(true){const {value,done}=await reader.read();if(done)throw Error('尚未收到历史帧');buffer+=decoder.decode(value,{stream:true}).replace(/\r/g,'');let end;while((end=buffer.indexOf('\n\n'))>=0){const frame=buffer.slice(0,end);buffer=buffer.slice(end+2);const id=frame.split('\n').find(x=>x.startsWith('id:'))?.slice(3).trim(),text=frame.split('\n').filter(x=>x.startsWith('data:')).map(x=>x.slice(5).trimStart()).join('\n');if(id&&text){const data=JSON.parse(text);if(data.checkpoint||data.delivery_id){const ack_attempts=[];if(data.delivery_id){for(let attempt=0;attempt<3;attempt++){const receipt=await fetch('/api/stream/ack-batch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify([{deliveryId:data.delivery_id,receivedAt:new Date().toISOString()}])});ack_attempts.push({at:new Date().toISOString(),http:receipt.status});if(receipt.ok)break;await new Promise(r=>setTimeout(r,500*(attempt+1)));}}return {id,data,connection_id:connectionId,ack_attempts};}}}}}finally{clearTimeout(timer);stop.abort();if(connectionId)await fetch('/api/stream/close',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({connectionId,kind:'CLIENT_REQUESTED'})});}},{cursor,source:event.source_id});
  const history=await read(String(BigInt(event.id)-1n));assert.equal(history.id,String(event.id));assert.equal(history.data.replay_flag,true);assert.notEqual(history.data.delivery_type,'LIVE');assert.equal(history.data.event_id,event.event_id);
  const ack=await pages[0].evaluate(async deliveryId=>{const r=await fetch('/api/stream/ack-batch',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify([{deliveryId,receivedAt:new Date().toISOString()}])});return r.status;},history.data.delivery_id);assert.equal(ack,200);
  const again=await read(history.id);assert(BigInt(again.id)>=BigInt(history.id));assert(again.data.checkpoint||again.data.event_id!==event.event_id);return {real_event:event,replay:history,ack_http:ack,reconnected:again,qualification:'真实历史补读，不是新增官方LIVE'};
 });
 await check('390像素手机页面及简体中文操作提示',async()=>{
  const page=pages[1];assert.equal(page.viewportSize().width,390);assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));
  const labels=await page.locator('button:visible,label:visible').allTextContents();assert(labels.some(x=>/自动|筛选|查询/.test(x)));assert(!labels.join('').match(/連線|觀測|環境|開發|錯誤|確認|實時|資料/));
  await page.locator('[data-event-id]').first().scrollIntoViewIfNeeded();await page.screenshot({path:prefix+'-390.png'});return {width:390,labels,visible_event:await page.locator('[data-event-id]').first().innerText(),document_lang:await page.locator('html').getAttribute('lang'),screenshot:path.basename(prefix)+'-390.png',scope:'中文操作控件；官方原文、机构缩写及证据标识保持原样'};
 });
 await check('S66静态入口与历史研究回读',async()=>{
  const page=pages[1];await page.goto(base+'/#/linkage?snapshot=a4da755b-507e-45e5-9362-ece080c53ee0');
  await until(async()=>await page.locator('.transmission').innerText().then(x=>x.includes('全球金融事件影响传导研究')),60000);
  await until(async()=>await page.locator('.transmission').innerText().then(x=>x.includes('0.0020')),60000);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2));await page.screenshot({path:prefix+'-s66-390.png',fullPage:true});
  return {path:page.url(),text:(await page.locator('.transmission').innerText()).slice(0,350),scope:'只读既有研究，不生成新快照'};
 });
 await check('原观察源码与配置、依赖清单未修改',async()=>{const mismatches=manifest.source_files.filter(x=>hash(path.join(manifest.source_root,x.path))!==x.sha256);assert.deepEqual(mismatches,[]);assert.notEqual(fs.realpathSync(path.join(candidate.work,'node_modules')),fs.realpathSync(path.join(manifest.source_root,'node_modules')));return {protected_files:manifest.source_files.length,changed:0,independent_dependencies:true};});
 await check('Outbox无异常积压与原Java身份保持',async()=>{const databaseAfter=backlog();assert.equal(databaseAfter.backlog,0);assert.equal(databaseBefore.postgres_started_at,databaseAfter.postgres_started_at);const identity=JSON.parse(fs.readFileSync(path.join(root,'runtime/s70-main-process.json'),'utf8').replace(/^\uFEFF/,''));const live=JSON.parse(execFileSync('powershell.exe',['-NoProfile','-Command',`Get-CimInstance Win32_Process -Filter "ProcessId=${identity.processId}" | Select-Object ProcessId,@{Name='createdAt';Expression={$_.CreationDate.ToUniversalTime().ToString('o')}},CommandLine | ConvertTo-Json`],{encoding:'utf8'}));assert.equal(Date.parse(live.createdAt),Date.parse(identity.createdAt));return {databaseBefore,databaseAfter,java:live};});
}finally{
 fs.writeFileSync(app,appBytes);fs.writeFileSync(config,configBytes);
 for(const browser of browsers)await browser.close().catch(()=>{});
 const report={at:new Date().toISOString(),candidate,scope:'隔离整改验证；未替换原窗口，不是正式LIVE或新24小时验收',results,trace,pageErrors,resource_hashes_before:before,resource_hashes_after:snapshot(),passed:results.length===10&&results.every(x=>x.status==='通过')};
 fs.writeFileSync(prefix+'.json',JSON.stringify(report,null,2),{flag:'wx'});console.log(JSON.stringify({report:prefix+'.json',passed:report.passed,checks:results.length}));if(!report.passed)process.exitCode=1;
}
