import {writeFileSync} from 'node:fs';
const token=process.env.HUANKAN_ADMIN_TOKEN;
if(!token)throw new Error('访问密钥未配置');
const headers={Authorization:'Bearer '+token};
async function replay(cursor,expected){
 // fixedDelay includes the database delivery journal and serial work for all clients.
 // Allow that measured cost; the production server remains bounded at 100 rows/cycle.
 const abort=new AbortController();const timer=setTimeout(()=>abort.abort(),Math.min(240000,Math.max(15000,Math.ceil(expected/100)*4000+15000)));const notices=[];
 try {
  const response=await fetch('http://127.0.0.1:18080/api/stream',{headers:{...headers,'Last-Event-ID':cursor},signal:abort.signal});
  if(!response.ok)throw new Error('SSE HTTP '+response.status);
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
  while(notices.length<expected){const {value,done}=await reader.read();if(done)throw new Error('SSE意外结束');buffer+=decoder.decode(value,{stream:true});let cut;
   while((cut=buffer.indexOf('\n\n'))>=0){const frame=buffer.slice(0,cut).replace(/\r/g,'');buffer=buffer.slice(cut+2);const lines=frame.split('\n'),id=lines.find(l=>l.startsWith('id:'))?.slice(3).trim(),data=lines.find(l=>l.startsWith('data:'))?.slice(5).trim();if(id&&data&&notices.length<expected)notices.push({id,...JSON.parse(data)});}
  }
  return notices;
 }finally{clearTimeout(timer);abort.abort();}
}
const started=Date.now();
const stats=await (await fetch('http://127.0.0.1:18080/api/stats',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:'{}'})).json();
const expected=Number(stats.outbox_count);if(expected<=0)throw new Error('暂无真实数据，无法验证历史回放');
const [a,b]=await Promise.all([replay('0',expected),replay('0',expected)]);
if(new Set(a.map(n=>n.id)).size!==a.length||a.map(n=>n.id).join(',')!==b.map(n=>n.id).join(','))throw new Error('双客户端消息不一致或重复');
const offset=Math.floor(expected/2), resumed=await replay(a[offset-1].id,expected-offset);
if(resumed.map(n=>n.id).join(',')!==a.slice(offset).map(n=>n.id).join(','))throw new Error('Last-Event-ID补读不一致');
const result={checked_at:new Date().toISOString(),actual_duration_seconds:(Date.now()-started)/1000,classification:'REAL_HISTORICAL_DATA_REPLAY_ONLY',clients:2,client_a_messages:a.length,client_b_messages:b.length,unique_messages:new Set(a.map(n=>n.id)).size,reconnect_cursor:a[offset-1].id,replayed_after_reconnect:resumed.length,historical_backfill_messages:a.filter(n=>n.notification_kind==='HISTORICAL_BACKFILL').length,new_event_messages:a.filter(n=>n.notification_kind==='NEW_EVENT').length,passed:true};
writeFileSync('docs/evidence/real-sse-replay.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));



