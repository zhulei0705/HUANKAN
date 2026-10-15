import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {pathToFileURL} from 'node:url';
const release=path.resolve(process.argv[2]||''),port=Number(process.argv[3]||5182),expected=process.argv[4];
if(![5173,5182,5183,5184].includes(port)||!expected||!fs.existsSync(path.join(release,'manifest.json')))throw Error('必须指定冻结版本、批准的静态端口和清单SHA256');
const hash=b=>crypto.createHash('sha256').update(b).digest('hex'),manifestBytes=fs.readFileSync(path.join(release,'manifest.json'));
if(hash(manifestBytes)!==expected)throw Error('冻结清单SHA256不匹配');
const manifest=JSON.parse(manifestBytes),assets=new Map();
if([5173,5183].includes(port)){
 if(!process.argv.includes('--user-confirmed'))throw Error('正式静态切换必须由用户明确确认，本轮仅准备方案');
 const root=path.resolve(import.meta.dirname,'..'),auditPath=process.argv[5];
 if(!auditPath)throw Error('正式静态入口必须指定原窗口最终冻结审计');
 const audit=JSON.parse(fs.readFileSync(path.resolve(auditPath),'utf8').replace(/^\uFEFF/,''));
 const h=JSON.parse(fs.readFileSync(path.join(root,'docs/evidence/s69-continuity-20261012-193258.json'))),b=JSON.parse(fs.readFileSync(path.join(root,'docs/evidence/s69-online-20261012-194753.json')));
 if(audit.final!==true||!h.finished_at||!b.finished_at||!b.complete||Date.parse(h.finished_at)>Date.now()||Date.parse(b.finished_at)>Date.now())throw Error('原窗口尚未实际结束并冻结，禁止切换正式静态入口');
 for(const item of audit.manifest){if(hash(fs.readFileSync(path.resolve(root,item.snapshot)))!==item.sha256)throw Error('原窗口冻结证据SHA256不匹配');}
}
for(const item of manifest.files){const p=path.resolve(release,item.path);if(!p.startsWith(release+path.sep))throw Error('清单路径越界');const bytes=fs.readFileSync(p);if(hash(bytes)!==item.sha256)throw Error('冻结资源SHA256不匹配：'+item.path);if(item.path.startsWith('public/'))assets.set('/'+item.path.slice(7),{bytes,sha256:item.sha256});}
const token=process.env.HUANKAN_ADMIN_TOKEN;if(!token)throw Error('本机服务配置未加载');
const isolated=port===5184;
if(isolated&&(!/^jdbc:postgresql:\/\/127\.0\.0\.1:55432\/huankan_test\?currentSchema=r1_web_[a-f0-9]{32}$/.test(process.env.HUANKAN_DB_URL||'')||process.env.HUANKAN_BACKEND_URL!=='http://127.0.0.1:18088'))throw Error('隔离静态测试必须绑定本次55432测试schema和18088，不得访问正式数据库');
const origins=new Set([`http://127.0.0.1:${port}`,`http://localhost:${port}`]);
const mime={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
const middlewares=[];
const {uiProjection}=await import(pathToFileURL(path.join(release,'ui-projection.mjs')).href);
if(!isolated)uiProjection().configureServer({config:{server:{port}},middlewares:{use:m=>middlewares.push(m)}});
function response(res,status,message){if(res.headersSent){res.destroy();return;}res.writeHead(status,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({message}));}
function route(req,res){
 const url=new URL(req.url,'http://127.0.0.1');
 if(url.pathname.startsWith('/api/')||url.pathname.startsWith('/actuator/')){
  const research=/^\/api\/(transmission|qualification|macro-changes|practical)(\/|$)/.test(url.pathname);
  const apiPort=isolated?18088:research?18087:18080;
  const headers={...req.headers,host:`127.0.0.1:${apiPort}`,authorization:'Bearer '+token};delete headers.connection;
  const upstream=http.request({hostname:'127.0.0.1',port:apiPort,path:req.url,method:req.method,headers},reply=>{
   res.writeHead(reply.statusCode,reply.headers);reply.pipe(res);reply.on('error',()=>res.destroy());
   if(url.pathname==='/api/stream')console.log(JSON.stringify({at:new Date().toISOString(),kind:'SSE连接',http:reply.statusCode,connection_id:reply.headers['x-stream-connection-id']}));
  });
  upstream.on('error',()=>response(res,502,'后端请求失败，请核对服务日志'));
  upstream.setTimeout(url.pathname==='/api/stream'?330000:45000,()=>upstream.destroy(new Error('后端请求超时')));
  res.on('close',()=>upstream.destroy());req.pipe(upstream);return;
 }
 if(!['GET','HEAD'].includes(req.method)){response(res,405,'仅允许读取静态资源');return;}
 const name=url.pathname==='/'?'/index.html':decodeURIComponent(url.pathname),asset=assets.get(name);
 if(!asset){response(res,404,'静态资源不存在');return;}
 res.writeHead(200,{'Content-Type':mime[path.extname(name)]||'application/octet-stream','Content-Length':asset.bytes.length,'Cache-Control':name.endsWith('.html')?'no-store':'public, max-age=31536000, immutable','ETag':'"'+asset.sha256+'"','X-Content-Type-Options':'nosniff','X-Huankan-Release':manifest.version});res.end(req.method==='HEAD'?undefined:asset.bytes);
}
const server=http.createServer((req,res)=>{
 if(!origins.has('http://'+req.headers.host)||(req.headers.origin&&!origins.has(req.headers.origin))||req.headers['sec-fetch-site']==='cross-site'){response(res,403,'仅允许本机页面访问');return;}
 let index=0;const next=()=>{const middleware=middlewares[index++];if(middleware)Promise.resolve(middleware(req,res,next)).catch(()=>response(res,500,'本机只读查询失败'));else try{route(req,res);}catch{response(res,400,'请求路径无效');}};next();
});
server.listen(port,'127.0.0.1',()=>console.log(JSON.stringify({at:new Date().toISOString(),message:'冻结静态前端就绪，资源已载入内存，不监听源码热更新',pid:process.pid,port,release,version:manifest.version,manifest_sha256:expected})));
server.on('error',e=>{console.error('静态前端启动失败：'+e.message);process.exitCode=1;});
