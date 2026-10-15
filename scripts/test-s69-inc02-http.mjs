import fs from 'node:fs';
const dir='docs/evidence/s69-inc02',base='http://127.0.0.1:18088',token=process.env.HUANKAN_INC02_TEST_TOKEN;
if(!token)throw Error('缺隔离令牌');
const report={at:new Date().toISOString(),scope:'仅原版18088直接HTTP/SSE；没有静态代理或浏览器补测，不计LIVE',tests:[]};
const save=()=>fs.writeFileSync(`${dir}/http-tests.json`,JSON.stringify(report,null,2));
async function test(name,fn){try{const details=await fn();report.tests.push({name,passed:true,details});}catch(e){report.tests.push({name,passed:false,error:String(e)});save();throw e;}save();}
const assert=(x,s)=>{if(!x)throw Error(s)};
async function post(path,body,auth=token){return fetch(base+path,{method:'POST',headers:{'Content-Type':'application/json',...(auth?{Authorization:'Bearer '+auth}:{})},body:JSON.stringify(body),signal:AbortSignal.timeout(4000)});}
await test('原版健康UP',async()=>{const r=await fetch(base+'/actuator/health');const b=await r.json();assert(r.status===200&&b.status==='UP','健康失败');return b});
for(const [name,auth] of [['无令牌shutdown拒绝',null],['错误令牌shutdown拒绝','invalid-token']])await test(name,async()=>{const r=await post('/actuator/shutdown',{},auth);await r.text();assert(r.status===401||r.status===403,'未拒绝shutdown');return {http:r.status}});
let cursor;
await test('拒绝未授权关闭后仍健康',async()=>{const r=await fetch(base+'/actuator/health');assert(r.status===200,'被未授权关闭');return {http:r.status}});
await test('Outbox游标可读',async()=>{const r=await post('/api/stream/cursor',{});const b=await r.json();cursor=Number(b.cursor);assert(r.status===200&&cursor>=1,'游标未读到保留事件');return b});
async function stream(start,wantEvent){
 const controller=new AbortController();const timer=setTimeout(()=>controller.abort(),5000);
 let reader,connection;const events=[];let buffer='';
 try{const r=await fetch(base+'/api/stream?scope=HISTORY',{headers:{Authorization:'Bearer '+token,'Last-Event-ID':String(start)},signal:controller.signal});assert(r.status===200,'SSE连接失败');assert((r.headers.get('content-type')||'').includes('text/event-stream'),'SSE类型错误');connection=r.headers.get('x-stream-connection-id');reader=r.body.getReader();const began=Date.now();
  while(Date.now()-began<(wantEvent?3500:1800)){
   const result=await Promise.race([reader.read(),new Promise(resolve=>setTimeout(()=>resolve({timeout:true}),wantEvent?3300:1700))]);if(result.timeout||result.done)break;
   buffer+=new TextDecoder().decode(result.value);let split;
   while((split=buffer.indexOf('\n\n'))>=0){const block=buffer.slice(0,split);buffer=buffer.slice(split+2);const name=block.match(/^event:(.*)$/m)?.[1]?.trim();const data=block.match(/^data:(.*)$/m)?.[1];if(name==='event'&&data)events.push(JSON.parse(data));}
   if(wantEvent&&events.length)break;
  }
  return {connection,events};
 }finally{clearTimeout(timer);controller.abort();try{await reader?.cancel()}catch{};if(connection)try{await (await post('/api/stream/close',{connectionId:connection,kind:'CLIENT_REQUESTED'})).text()}catch{}}
}
let replay;
await test('SSE网络连接与历史Outbox补读',async()=>{replay=await stream(0,true);assert(replay.events.length===1,'未补读到单条既有测试记录');const e=replay.events[0];assert(e.replay_flag===true&&e.delivery_type!=='LIVE','历史记录错误标LIVE');return {connection:replay.connection,events:replay.events.map(e=>({id:e.id,evidence_id:e.evidence_id,delivery_type:e.delivery_type,replay_flag:e.replay_flag}))}});
await test('客户端确认历史补读',async()=>{const e=replay.events[0];const r=await post('/api/stream/ack',{deliveryId:e.delivery_id,receivedAt:new Date().toISOString()});assert(r.status===200,'ACK失败');return {http:r.status}});
await test('同游标重连不重复发历史记录',async()=>{const r=await stream(cursor,false);assert(r.events.length===0,'游标重连重复推送');return {cursor,events:r.events.length,connection:r.connection}});
report.completed=true;report.passed=true;report.count=report.tests.length;save();console.log(JSON.stringify(report,null,2));
