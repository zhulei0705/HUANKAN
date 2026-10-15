import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const headers={Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN};
const sql=q=>JSON.parse(execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-At','-v','ON_ERROR_STOP=1','-c',q],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},encoding:'utf8'}));
const sample=sql(`SELECT row_to_json(t) FROM (SELECT r.id evidence_id,e.id event_id,e.original_title,r.source_url,r.published_at,r.first_seen_at,r.ingested_at,r.raw_content_hash,encode(r.raw_content,'base64') raw,o.id outbox_id,o.created_at outbox_created_at FROM raw_evidence r JOIN financial_event e ON e.evidence_id=r.id JOIN stream_outbox o ON o.evidence_id=r.id WHERE r.source_id='FED' AND r.ingestion_kind='NEW_EVENT' ORDER BY r.ingested_at DESC LIMIT 1)t;`);
if(!sample)throw new Error('没有真实新增官方样本');
if(createHash('sha256').update(Buffer.from(sample.raw,'base64')).digest('hex')!==sample.raw_content_hash)throw new Error('实际新增证据哈希不一致');
const url=new URL(sample.source_url);if(url.protocol!=='https:'||url.hostname!=='www.federalreserve.gov')throw new Error('原文不属于美联储官方来源');
const official=await fetch(url,{signal:AbortSignal.timeout(30000)});if(!official.ok)throw new Error('官方原文 HTTP '+official.status);const actual=await official.text();if(!actual.includes('Survey of Consumer Finances'))throw new Error('官方原文未包含本次发布主题');
async function replay(){
 const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),20000);let connectionId;
 try{
  const response=await fetch('http://127.0.0.1:18080/api/stream',{headers:{...headers,'Last-Event-ID':String(BigInt(sample.outbox_id)-1n)},signal:abort.signal});if(!response.ok)throw new Error('SSE HTTP '+response.status);connectionId=response.headers.get('X-Stream-Connection-Id');
  const reader=response.body.getReader(),decoder=new TextDecoder();let buffer='';
  while(true){const {value,done}=await reader.read();if(done)throw new Error('SSE提前结束');buffer+=decoder.decode(value,{stream:true});let cut;
   while((cut=buffer.indexOf('\n\n'))>=0){const frame=buffer.slice(0,cut).replace(/\r/g,'');buffer=buffer.slice(cut+2);const data=frame.split('\n').find(x=>x.startsWith('data:'))?.slice(5).trim();if(!data)continue;const n=JSON.parse(data);if(String(n.id)!==String(sample.outbox_id))continue;if(n.evidence_id!==sample.evidence_id||n.notification_kind!=='NEW_EVENT')throw new Error('新增消息证据引用或分类不一致');const receivedAt=new Date().toISOString();await new Promise(r=>setTimeout(r,250));const ack=await fetch('http://127.0.0.1:18080/api/stream/ack',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({deliveryId:n.delivery_id,receivedAt})});if(!ack.ok)throw new Error('真实回放 ACK HTTP '+ack.status);return {delivery_id:n.delivery_id,server_sent_at:n.server_sent_at,received_at:receivedAt};}
  }
 }finally{clearTimeout(timer);if(connectionId)await fetch('http://127.0.0.1:18080/api/stream/close',{method:'POST',headers:{...headers,'Content-Type':'application/json'},body:JSON.stringify({connectionId,kind:'CLIENT_CLOSED'})});abort.abort();}
}
const clients=await Promise.all([replay(),replay()]);delete sample.raw;
const receipts=sql(`SELECT json_agg(t) FROM (SELECT id,sent_at,client_received_at,acknowledged_at,replay FROM stream_delivery WHERE id IN('${clients[0].delivery_id}','${clients[1].delivery_id}'))t;`);
if(receipts.length!==2||receipts.some(x=>!x.acknowledged_at||!x.replay))throw new Error('真实回放接收确认不完整');
const result={checked_at:new Date().toISOString(),classification:'REAL_NEW_OFFICIAL_RECORD_PERSISTED_REPLAY',sample,official_original_http:official.status,clients,receipts,published_to_discovery_seconds:(Date.parse(sample.first_seen_at)-Date.parse(sample.published_at))/1000,discovery_to_ingestion_ms:Date.parse(sample.ingested_at)-Date.parse(sample.first_seen_at),commit_time_live_browser_push_verified:false,passed:true};
writeFileSync('docs/evidence/s22-real-new-official-replay.json',JSON.stringify(result,null,2));console.log(JSON.stringify(result));
