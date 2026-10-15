import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
const root=path.resolve(import.meta.dirname,'..'),source=path.join(root,'frontend');
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,'');
const work=path.join(root,'runtime','s69-isolation',stamp,'development'),release=path.join(root,'runtime','s69-releases',stamp);
fs.mkdirSync(work,{recursive:true});if(fs.existsSync(release))throw Error('构建版本已存在，拒绝覆盖');
export function files(directory){const rows=[];for(const e of fs.readdirSync(directory,{withFileTypes:true})){const p=path.join(directory,e.name);if(e.isSymbolicLink())throw Error('拒绝共享链接：'+p);if(e.isDirectory())rows.push(...files(p));else rows.push(p);}return rows;}
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const excluded=/^(node_modules|dist(?:-.*)?|test-results|playwright-report)$/;
const inputs=[];for(const e of fs.readdirSync(source,{withFileTypes:true})){if(excluded.test(e.name))continue;const p=path.join(source,e.name);if(e.isSymbolicLink())throw Error('源码存在链接，拒绝复制');fs.cpSync(p,path.join(work,e.name),{recursive:true});for(const f of e.isDirectory()?files(p):[p])inputs.push({path:path.relative(source,f).replaceAll('\\','/'),sha256:hash(f)});}
fs.copyFileSync(path.join(root,'scripts','vite.s69-isolated.template.ts'),path.join(work,'vite.s69-isolated.config.ts'));
const npm=path.join(path.dirname(process.execPath),'node_modules','npm','bin','npm-cli.js');
function run(args,name){const log=path.join(root,'runtime',`s69-isolation-${stamp}-${name}.log`);try{const output=execFileSync(process.execPath,[npm,...args],{cwd:work,encoding:'utf8',maxBuffer:20*1024*1024});fs.writeFileSync(log,output,{flag:'wx'});console.log(name+'完成');}catch(e){fs.writeFileSync(log,(e.stdout||'')+(e.stderr||''),{flag:'wx'});throw e;}}
run(['ci','--offline','--no-audit','--no-fund'],'dependencies');
run(['exec','--','vue-tsc','--noEmit'],'typecheck');
run(['test'],'unit-tests');
fs.mkdirSync(release,{recursive:true});
run(['exec','--','vite','build','--config','vite.s69-isolated.config.ts','--outDir',path.join(release,'public'),'--emptyOutDir'],'static-build');
const {build}=await import(pathToFileURL(path.join(work,'node_modules','esbuild','lib','main.js')).href);
await build({entryPoints:[path.join(work,'uiProjection.ts')],bundle:true,platform:'node',format:'esm',outfile:path.join(release,'ui-projection.mjs')});
const manifest={version:stamp,built_at:new Date().toISOString(),source_root:source,development_root:work,release_root:release,source_files:inputs,files:files(release).map(p=>({path:path.relative(release,p).replaceAll('\\','/'),sha256:hash(p),bytes:fs.statSync(p).size})),purpose:'隔离候选静态前端；未替换原S69页面，不计正式LIVE或24小时窗口'};
fs.writeFileSync(path.join(release,'manifest.json'),JSON.stringify(manifest,null,2),{flag:'wx'});
fs.writeFileSync(path.join(root,'docs','evidence',`s69-isolation-release-${stamp}.json`),JSON.stringify({...manifest,manifest_sha256:hash(path.join(release,'manifest.json'))},null,2),{flag:'wx'});
const candidateFile=process.argv[2]||'runtime/s69-isolation-candidate.json';
if(!/^runtime\/s69[-a-z0-9]*\.json$/.test(candidateFile))throw Error('候选索引路径无效');
fs.writeFileSync(path.join(root,candidateFile),JSON.stringify({stamp,work,release,manifest_sha256:hash(path.join(release,'manifest.json'))},null,2));
console.log(JSON.stringify({stamp,work,release,source_files:inputs.length,release_files:manifest.files.length}));
