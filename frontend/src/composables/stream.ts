export function parseFrame(frame:string):{id:string;data:unknown}|null {
 const lines=frame.replace(/\r/g,'').split('\n');
 const id=lines.find(l=>l.startsWith('id:'))?.slice(3).trim();
 const data=lines.filter(l=>l.startsWith('data:')).map(l=>l.slice(5).trimStart()).join('\n');
 if(!id||!data)return null;
 return {id,data:JSON.parse(data)};
}
export type Notice={id:string;data:Record<string,unknown>};
export async function stream(token:string,signal:AbortSignal,onEvent:(events:Notice[])=>Promise<void>,onStatus:(s:string)=>void,initialCursor='0',scope='OFFICIAL',sources='',priority=0,watchOnly=false) {
 let cursor=initialCursor;
 let connectionId:unknown;
 while(!signal.aborted){
  const connection=new AbortController();
  const stop=()=>{if(connectionId)void fetch('/api/stream/close',{method:'POST',keepalive:true,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({connectionId,kind:'CLIENT_REQUESTED'})}).catch(()=>{});connection.abort();};
  const pagehide=()=>{if(connectionId)void fetch('/api/stream/close',{method:'POST',keepalive:true,headers:{'Content-Type':'application/json',Authorization:'Bearer '+token},body:JSON.stringify({connectionId,kind:'BROWSER_CLOSED'})}).catch(()=>{});};
  const offline=()=>{onStatus('实时连接已断开');connection.abort();};
  signal.addEventListener('abort',stop,{once:true});
  if(typeof window!=='undefined'){window.addEventListener('offline',offline);window.addEventListener('pagehide',pagehide);}
  if(typeof navigator!=='undefined'&&!navigator.onLine)offline();
  try {
   const response=await fetch('/api/stream?scope='+encodeURIComponent(scope)+'&sources='+encodeURIComponent(sources)+'&priority='+priority+'&watchOnly='+watchOnly,{headers:{Authorization:'Bearer '+token,'Last-Event-ID':cursor},signal:connection.signal});
   if(!response.ok||!response.body)throw new Error('推送连接失败');
   connectionId=response.headers.get('X-Stream-Connection-Id');
   onStatus('已连接');const reader=response.body.getReader();const decoder=new TextDecoder();let buffer='';
   let queued:Notice[]=[];let timer:ReturnType<typeof setTimeout>|undefined;let flushing=Promise.resolve();let failed=false;
   const flush=()=>{flushing=flushing.then(async()=>{const batch=queued;queued=[];if(!batch.length||signal.aborted||failed)return;await onEvent(batch);cursor=batch[batch.length-1].id;}).catch(()=>{failed=true;onStatus('实时连接已断开');void reader.cancel();});};
   try {while(!signal.aborted&&!connection.signal.aborted){const {value,done}=await reader.read();if(done)break;buffer+=decoder.decode(value,{stream:true}).replace(/\r/g,'');
    if(buffer.length>1024*1024)throw new Error('推送帧超限');
    let boundary;while((boundary=buffer.indexOf('\n\n'))>=0){const frame=parseFrame(buffer.slice(0,boundary));buffer=buffer.slice(boundary+2);if(frame&&/^\d+$/.test(frame.id)&&BigInt(frame.id)>BigInt(cursor)&&!queued.some(n=>n.id===frame.id)){
      if(queued.length>=1000)throw new Error('补读队列达到上限，将从已确认游标恢复');
      const notice=frame as Notice;connectionId=notice.data.connection_id;notice.data.client_received_at=new Date().toISOString();notice.data.received_monotonic_ms=performance.now();queued.push(notice);if(queued.length>=100){if(timer)clearTimeout(timer);timer=undefined;flush();}else if(!timer)timer=setTimeout(()=>{timer=undefined;flush();},300);
    }}
   }} finally {if(timer)clearTimeout(timer);flush();await flushing;reader.releaseLock();}
   if(signal.aborted)return;onStatus('实时连接已断开');
  }catch{if(signal.aborted)return;onStatus('实时连接已断开');}
  finally {signal.removeEventListener('abort',stop);if(typeof window!=='undefined'){window.removeEventListener('offline',offline);window.removeEventListener('pagehide',pagehide);}}
  if(!signal.aborted)await new Promise<void>(resolve=>{const timer=setTimeout(resolve,3000);signal.addEventListener('abort',()=>{clearTimeout(timer);resolve();},{once:true});});
 }
}
