import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const read=p=>JSON.parse(fs.readFileSync(p)),hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const candidate=read('runtime/s69-r1-candidate.json'),manifest=read(path.join(candidate.release,'manifest.json'));
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),directory=path.resolve('runtime','s69-r2-rollback',stamp),frontend=path.join(directory,'frontend');
fs.mkdirSync(frontend,{recursive:true});const files=[];
for(const input of manifest.source_files){const source=path.join(manifest.source_root,input.path);if(hash(source)!==input.sha256)throw Error('原源码已变化，拒绝生成不一致回滚镜像：'+input.path);const target=path.join(frontend,input.path);fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(source,target,fs.constants.COPYFILE_EXCL);files.push({path:input.path,sha256:hash(target)});}
const backend=path.join(directory,'huankan-s70-reviewed-0.1.0.jar');fs.copyFileSync('backend/target/huankan-s70-reviewed-0.1.0.jar',backend,fs.constants.COPYFILE_EXCL);
const npm=path.join(path.dirname(process.execPath),'node_modules/npm/bin/npm-cli.js'),log=`runtime/s69-r2-rollback-${stamp}-dependencies.log`;
try{const output=execFileSync(process.execPath,[npm,'ci','--offline','--no-audit','--no-fund'],{cwd:frontend,encoding:'utf8',maxBuffer:8*1024*1024});fs.writeFileSync(log,output,{flag:'wx'});}catch(e){fs.writeFileSync(log,(e.stdout||'')+(e.stderr||''),{flag:'wx'});throw e;}
const record={at:new Date().toISOString(),directory,frontend,backend,backend_sha256:hash(backend),files,dependencies:{lock_sha256:hash(path.join(frontend,'package-lock.json')),vite_executable_sha256:hash(path.join(frontend,'node_modules/vite/bin/vite.js')),independent_node_modules:fs.realpathSync(path.join(frontend,'node_modules'))!==fs.realpathSync(path.join(manifest.source_root,'node_modules')),log},scope:'仅生成旧版本回滚镜像，未绑定5173或停止正式进程；没有宣称真实回滚成功'};
const file=`docs/evidence/s69-r2-rollback-${stamp}.json`;fs.writeFileSync(file,JSON.stringify(record,null,2),{flag:'wx'});console.log(JSON.stringify({file,frontend,backend,protected_files:files.length,scope:record.scope}));
