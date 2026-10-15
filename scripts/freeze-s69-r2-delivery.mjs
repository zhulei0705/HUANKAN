import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),file=`docs/evidence/s69-r2-delivery-${stamp}.json`,directory=`docs/evidence/s69-r2-delivery-${stamp}-files`;
fs.mkdirSync(directory,{recursive:false});
const documents=['docs/S69-R2-连接池与调度可观测性验收.md','docs/S69-R2-正式部署与静态前端准入报告.md','docs/S69-R2-新24小时回归预检报告.md','docs/统一开发状态报告.md'];
const queryFailures=[['s69-r2-isolated-sse-failure-database.json','诊断查询字符编码不匹配，PostgreSQL报告UTF8 0xd7 0xd4序列无效'],['s69-r2-isolated-sse-failure-database-retry.json','误引用不存在的stream_delivery.event_id'],['s69-r2-isolated-sse-failure-database-final.json','误引用不存在的stream_connection.connected_at']].map(([name,error])=>{const p='docs/evidence/'+name;const exists=fs.existsSync(p);return {intended_file:p,error,artifact_created:exists,bytes:exists?fs.statSync(p).size:null,artifact_modified_at:exists?fs.statSync(p).mtime.toISOString():null,qualified_evidence:false};});
fs.writeFileSync('docs/evidence/s69-r2-diagnostic-query-failures.json',JSON.stringify({scope:'实际实施失败记录；mtime不是推定的精确请求时刻，空文件不能充作通过证据',failures:queryFailures,verified_result:'docs/evidence/s69-r2-isolated-sse-failure-database-verified.json'},null,2),{flag:'wx'});
for(const doc of documents)fs.appendFileSync(doc,`\n\n## S69-R2交付冻结索引\n\n[本轮不可变交付副本与SHA-256清单](evidence/${path.basename(file)})包含代码、候选JAR、最终/失败日志、测试XML/JSON、截图、schema导出及报告副本；不改原R1索引或哈希。新增交付脚本scripts/freeze-s69-r2-delivery.mjs。诊断查询曾因编码及字段引用失败、未形成有效JSON，[错误记录](evidence/s69-r2-diagnostic-query-failures.json)保留，最终按verified.json有效查询判断；未形成的文件不计验收通过。\n`);
// In the status report the relative evidence links resolve from docs, just as in other reports.
function files(dir){return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name).replaceAll('\\','/');return e.isDirectory()?files(p):[p];});}
const sources=new Set(documents);
for(const p of files('backend/src'))if(p.includes('/reliability/')||p.endsWith('/CollectorGuard.java')||p.endsWith('/application.yml'))sources.add(p);
sources.add('backend/pom.xml');sources.add('backend/target/huankan-s69-r2-candidate-0.1.0.jar');
for(const name of fs.readdirSync('scripts'))if(name.includes('s69-r2'))sources.add('scripts/'+name);
for(const name of fs.readdirSync('runtime'))if(name.includes('s69-r2')&&fs.statSync('runtime/'+name).isFile())sources.add('runtime/'+name);
for(const name of fs.readdirSync('docs/evidence'))if(name.startsWith('s69-r2-')&&name!==path.basename(file)&&name!==path.basename(directory))for(const p of fs.statSync('docs/evidence/'+name).isDirectory()?files('docs/evidence/'+name):['docs/evidence/'+name])sources.add(p);
for(const p of files('backend/target/surefire-reports'))sources.add(p);
const test=read('runtime/s69-r2-test-process.json'),release=test.candidate.release;
for(const name of ['manifest.json',...read(path.join(release,'manifest.json')).files.map(x=>x.path)])sources.add(path.join(release,name));
const rollbackFile=fs.readdirSync('docs/evidence').filter(x=>/^s69-r2-rollback-.*\.json$/.test(x)).sort().at(-1),rollback=read('docs/evidence/'+rollbackFile);
sources.add(rollback.backend);for(const input of rollback.files)sources.add(path.join(rollback.frontend,input.path));
sources.add(path.join(rollback.frontend,'node_modules/vite/bin/vite.js'));
const manifest=[];let index=0;
for(const source of [...sources].sort()){const snapshot=path.join(directory,String(index++).padStart(4,'0')+path.extname(source)).replaceAll('\\','/');fs.copyFileSync(source,snapshot,fs.constants.COPYFILE_EXCL);manifest.push({source,snapshot,bytes:fs.statSync(snapshot).size,sha256:hash(snapshot)});}
const original='docs/evidence/s69-r1-final-index-20261013T115021.json';
const report={at:new Date().toISOString(),scope:'S69-R2交付单独冻结；正式Java/5173/PostgreSQL未重启或切换，未新建24小时窗口',original_index:{file:original,sha256:hash(original),expected:'3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14'},manifest,qualification:'只读/隔离测试与部署方案，不代表正式部署、真实回滚或官方双端LIVE通过'};
fs.writeFileSync(file,JSON.stringify(report,null,2),{flag:'wx'});console.log(JSON.stringify({file,sha256:hash(file),files:manifest.length,original_index_unchanged:report.original_index.sha256===report.original_index.expected}));
