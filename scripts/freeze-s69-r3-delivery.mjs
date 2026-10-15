import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,'');
const file=`docs/evidence/s69-r3-delivery-${stamp}.json`,directory=`docs/evidence/s69-r3-delivery-${stamp}-files`;
const documents=['docs/S69-R3-JDBC连接生命周期专项验收.md','docs/S69-R3-调度竞争与负载回归验收.md','docs/S69-R3-正式部署最终准入建议.md','docs/统一开发状态报告.md'];
for(const doc of documents)if(!fs.existsSync(doc))throw Error('报告尚未完成：'+doc);
const artifacts=fs.readdirSync('docs/evidence').filter(n=>/^s69-r3-artifacts-.*\.json$/.test(n)).sort().at(-1);
if(!read('docs/evidence/'+artifacts).passed)throw Error('资源验收未通过，不标为成功冻结');
fs.mkdirSync(directory,{recursive:false});
for(const doc of documents)fs.appendFileSync(doc,`\n\n## S69-R3交付冻结索引\n\n[本轮不可变副本与SHA-256清单](evidence/${path.basename(file)})包含候选代码/JAR、成功与失败日志、测试XML/JSON、完整隔离记录、截图和报告副本。旧R1/R2索引及原始哈希不改；冻结不代表正式部署或官方LIVE通过。\n`);
const files=dir=>fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>{const p=path.join(dir,e.name);return e.isDirectory()?files(p):[p];});
const sources=new Set(documents);
for(const p of files('backend/src'))sources.add(p);
sources.add('backend/pom.xml');sources.add('backend/target/huankan-s69-r3-candidate-0.1.0.jar');
for(const p of files('backend/target/surefire-reports'))sources.add(p);
for(const name of fs.readdirSync('scripts'))if(name.includes('s69-r3'))sources.add('scripts/'+name);
for(const name of fs.readdirSync('runtime'))if(name.includes('s69-r3')&&fs.statSync('runtime/'+name).isFile())sources.add('runtime/'+name);
for(const name of fs.readdirSync('docs/evidence'))if(name.startsWith('s69-r3-')&&name!==path.basename(file)&&name!==path.basename(directory)){
 const p='docs/evidence/'+name;for(const f of fs.statSync(p).isDirectory()?files(p):[p])sources.add(f);
}
const candidate=read('runtime/s69-r1-candidate.json');
for(const p of files(path.join(candidate.work,'e2e-r3')))sources.add(p);
sources.add(path.join(candidate.work,'playwright.r3.config.ts'));
for(const name of ['manifest.json',...read(path.join(candidate.release,'manifest.json')).files.map(x=>x.path)])sources.add(path.join(candidate.release,name));
const manifest=[];let n=0;for(const source of [...sources].sort()){
 const snapshot=path.join(directory,String(n++).padStart(4,'0')+path.extname(source)).replaceAll('\\','/');
 fs.copyFileSync(source,snapshot,fs.constants.COPYFILE_EXCL);manifest.push({source,snapshot,bytes:fs.statSync(snapshot).size,sha256:hash(snapshot)});
}
const original='docs/evidence/s69-r1-final-index-20261013T115021.json',prior='docs/evidence/s69-r2-delivery-20261014T014238.json';
const prior_checks=read(prior).manifest.map(x=>({snapshot:x.snapshot,sha256:x.sha256,match:hash(x.snapshot)===x.sha256}));
const original_checks=read(original).manifest.map(x=>{const source=fs.readFileSync(x.source),snapshot=fs.readFileSync(x.snapshot);return {source:x.source,snapshot:x.snapshot,sha256:x.sha256,frozen_match:hash(x.snapshot)===x.sha256,source_prefix_unchanged:source.length>=snapshot.length&&source.subarray(0,snapshot.length).equals(snapshot)};});
const result={at:new Date().toISOString(),scope:'S69-R3独立交付冻结；未部署/重启正式Java或PG，未切换5173，未新建24小时窗口或进入S67',
 original_index:{file:original,sha256:hash(original),expected:'3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14'},
 prior_index:{file:prior,sha256:hash(prior),expected:'02f5460a8f08313cc663c16b52455597052754a211a9904a5c3f4e7a6218f7f7'},prior_checks,original_checks,manifest,
 qualification:'隔离目标整改和回归，不证明正式根治、全容量、实际回滚或官方LIVE'};
fs.writeFileSync(file,JSON.stringify(result,null,2),{flag:'wx'});
const mismatch=prior_checks.filter(x=>!x.match).length+original_checks.filter(x=>!x.frozen_match||!x.source_prefix_unchanged).length;
console.log(JSON.stringify({file,sha256:hash(file),files:manifest.length,prior_files:prior_checks.length,mismatch,original_index_unchanged:result.original_index.sha256===result.original_index.expected,prior_index_unchanged:result.prior_index.sha256===result.prior_index.expected}));
if(mismatch||result.original_index.sha256!==result.original_index.expected||result.prior_index.sha256!==result.prior_index.expected)process.exitCode=1;
