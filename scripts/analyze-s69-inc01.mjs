import fs from 'node:fs';import crypto from 'node:crypto';
const dir='docs/evidence/s69-inc01/20261014T063813Z';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));const sha=b=>crypto.createHash('sha256').update(b).digest('hex');
const names=fs.readdirSync('runtime').filter(n=>/^s70-(main|research|web-)|^s6[456].*(research|service)|^isolated-s6[456]|^s69-r4-jvm-/.test(n)&&n.endsWith('.log'));
const logs=names.map(n=>{const lines=fs.readFileSync('runtime/'+n,'utf8').split(/\r?\n/);const timestamps=lines.filter(x=>/^2026-/.test(x));return {file:'runtime/'+n,lastTimestampedLine:timestamps.at(-1),lastLine:lines.filter(x=>x.trim()).at(-1),exitMarkers:lines.map((text,i)=>({line:i+1,text})).filter(x=>/OutOfMemoryError|A fatal error|hs_err|ShutdownHook|shutdown hook|Shutting down|Shutdown initiated|Shutdown completed|Closing.*ApplicationContext/i.test(x.text)),jdbcLast:lines.map((text,i)=>({line:i+1,text})).filter(x=>/JDBC|SQLTransientConnectionException|SQLState|HikariPool-1 - Connection is not available|PgConnection|DataAccessResourceFailureException/.test(x.text)).at(-1)};});
const audit={at:new Date().toISOString(),logs,windowsCommands:[],relevantEvents:[]};
for(const n of ['Windows_PowerShell.json','Microsoft-Windows-PowerShell_Operational.json']){
 const data=read(`${dir}/${n}`);for(const e of data.events||[]){if(/Stop-Process|taskkill|pg_ctl[^\r\n]{0,150}(stop|restart)|Kill\(|actuator\/shutdown/i.test(e.message||e.xml))audit.windowsCommands.push({file:n,...e});}
}
for(const n of ['Application.json','System.json'])for(const e of read(`${dir}/${n}`).events||[])if(/Codex|ChatGPT|java|postgres|Memory|资源/.test(e.message||e.xml))audit.relevantEvents.push({file:n,...e});
fs.writeFileSync(`${dir}/analysis.json`,JSON.stringify(audit,null,2),{flag:'wx'});
console.log(JSON.stringify({logs,relevantEvents:audit.relevantEvents.map(e=>({time:e.time,id:e.id,provider:e.provider,message:e.message,xml:!e.message?e.xml:undefined})),windowsCommandCount:audit.windowsCommands.length,windowsCommandTimes:audit.windowsCommands.map(e=>({time:e.time,id:e.id,message:e.message?.slice(0,650)}))},null,2));
