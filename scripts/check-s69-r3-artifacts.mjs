import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const candidate=read('runtime/s69-r1-candidate.json'),manifest=read(path.join(candidate.release,'manifest.json'));
const old=read('docs/evidence/s69-r2-static-hashes.json');
const protected_sources=old.protected_sources.map(x=>({...x,matches:hash(path.join('frontend',x.file))===hash(path.join('runtime/s69-r2-rollback/20261014T012640/frontend',x.file))}));
const assets=manifest.files.map(x=>({path:x.path,expected:x.sha256,actual:hash(path.join(candidate.release,x.path)),match:hash(path.join(candidate.release,x.path))===x.sha256,
 rebuilt:x.path==='ui-projection.mjs'?null:hash(path.join('runtime/s69-r3-static-rebuild',x.path))}));
const tests=fs.readdirSync('backend/target/surefire-reports').filter(n=>n.startsWith('TEST-')&&n.endsWith('.xml')).map(n=>{
 const p='backend/target/surefire-reports/'+n,xml=fs.readFileSync(p,'utf8'),header=xml.match(/<testsuite\b[^>]*>/)?.[0];
 const value=k=>Number(header?.match(new RegExp(k+'="([0-9.]+)"'))?.[1]);
 return {file:p,sha256:hash(p),tests:value('tests'),failures:value('failures'),errors:value('errors'),skipped:value('skipped')};
});
const totals=tests.reduce((r,t)=>{for(const k of ['tests','failures','errors','skipped'])r[k]+=t[k];return r;},{tests:0,failures:0,errors:0,skipped:0});
const log='runtime/s69-r3-backend-verified.log',text=fs.readFileSync(log,'utf8');
if(!text.includes('[INFO] BUILD SUCCESS')||totals.failures||totals.errors||totals.skipped)throw Error('最终完整回归尚未通过，不生成通过结论');
const scenarios=text.split(/\r?\n/).filter(x=>x.startsWith('S69_R1 ')).map(x=>JSON.parse(x.slice(7)));
const out={at:new Date().toISOString(),candidate,protected_sources,assets,manifest_sha256:hash(path.join(candidate.release,'manifest.json')),
 backend:{totals,tests,log,log_sha256:hash(log)},scenarios,
 jar:{file:'backend/target/huankan-s69-r3-candidate-0.1.0.jar',sha256:hash('backend/target/huankan-s69-r3-candidate-0.1.0.jar')},
 prior_failures:['runtime/s69-r3-lifecycle-first.log','runtime/s69-r3-lifecycle-second.log','runtime/s69-r3-backend-final-reviewed.log','runtime/s69-r3-backend-final-complete.log'].map(file=>({file,sha256:hash(file)})),
 passed:protected_sources.every(x=>x.matches)&&assets.every(x=>x.match&&(x.rebuilt===null||x.rebuilt===x.expected))};
const file='docs/evidence/s69-r3-artifacts-'+Date.now()+'.json';fs.writeFileSync(file,JSON.stringify(out,null,2),{flag:'wx'});
console.log(JSON.stringify({file,backend:totals,protected_sources:protected_sources.length,assets:assets.length,passed:out.passed,jar:out.jar}));
