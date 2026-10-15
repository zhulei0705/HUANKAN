import fs from 'node:fs';import crypto from 'node:crypto';import path from 'node:path';
const dest='docs/evidence/s69-inc01/20261014T063813Z';
const home='C:/Users/sthl/.codex';
const hash=b=>crypto.createHash('sha256').update(b).digest('hex');
const clean=s=>s.replace(/((?:PASSWORD|TOKEN|API_KEY)\s*[:=]\s*)[^\s,;]+/gi,'$1[REDACTED]');
const result={at:new Date().toISOString(),sources:[],actions:[],denials:[],observations:[],limits:'只审核本机10月14日既有HUANKAN相关会话；引用工具调用和结果，不以代理文字自证操作'};
for(const name of fs.readdirSync(`${home}/sessions/2026/10/14`)){
 if(!name.endsWith('.jsonl')||!name.includes('01a138ca'))continue;
 const file=`${home}/sessions/2026/10/14/${name}`,buffer=fs.readFileSync(file),lines=buffer.toString('utf8').split('\n');
 result.sources.push({file,sha256:hash(buffer),bytes:buffer.length});
 for(let i=0;i<lines.length;i++){
  let r;try{r=JSON.parse(lines[i])}catch{continue}
  const p=r.payload||{},isCall=p.type==='function_call'||p.type==='custom_tool_call',isOutput=p.type==='function_call_output'||p.type==='custom_tool_call_output';
 let s=typeof p.arguments==='string'?p.arguments:typeof p.input==='string'?p.input:typeof p.output==='string'?p.output:Array.isArray(p.output)?p.output.map(x=>x.text||'').join('\n'):JSON.stringify(p);
  const entry={file,line:i+1,time:r.timestamp,type:p.type,tool:p.name,callId:p.call_id};
 if(isCall&&/exec_command/.test(s)&&/Stop-Process|taskkill|pg_ctl.{0,180}(stop|restart)|\.Kill\(|actuator\/shutdown|close.*process|terminate/i.test(s))result.actions.push({...entry,text:clean(s)});
 if(isOutput&&/blocked by policy/i.test(s)){const call=lines.slice(0,i).map(l=>{try{return JSON.parse(l)}catch{return null}}).reverse().find(x=>x?.payload?.call_id===p.call_id);result.denials.push({...entry,text:clean(s),precedingTool:call?clean(call.payload.input||call.payload.arguments||''):null});}
  if(isOutput&&/18080|43884/.test(s)&&r.timestamp>='2026-10-14T05:15:00Z'&&r.timestamp<='2026-10-14T05:40:00Z')result.observations.push({...entry,text:clean(s).slice(0,24000)});
 }
}
fs.writeFileSync(`${dest}/agent-history-v2.json`,JSON.stringify(result,null,2),{flag:'wx'});
console.log(JSON.stringify({sources:result.sources,actions:result.actions.map(x=>({...x,text:x.text.slice(0,1200)})),denials:result.denials.map(x=>({...x,precedingTool:x.precedingTool.slice(0,1200)})),observations:result.observations.map(x=>({time:x.time,line:x.line,file:x.file,text:x.text.slice(0,800)}))},null,2));
