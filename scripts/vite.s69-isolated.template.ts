import {defineConfig,type ProxyOptions} from 'vite';
import vue from '@vitejs/plugin-vue';
import {uiProjection} from './uiProjection';
const token=process.env.HUANKAN_ADMIN_TOKEN;
const port=5181;
const proxy=(target:string):ProxyOptions=>({target,configure(server){server.on('proxyReq',r=>{r.removeHeader('authorization');if(token)r.setHeader('Authorization','Bearer '+token);});}});
export default defineConfig({
 plugins:[{name:'s69-isolated-local-entry',enforce:'pre',transform(code,id){if(!id.endsWith('.vue'))return;return code
 .replace("['5173','5176']","['5173','5176','5181','5182','5183']")
 .replace("['5179','5180']","['5173','5179','5180','5181','5182','5183']")
 .replace("location.port==='5180'","['5173','5180','5181','5182','5183'].includes(location.port)");}},vue(),uiProjection(),
 {name:'s69-local-access',configureServer(server){server.middlewares.use((req,res,next)=>{
  if(!req.url?.startsWith('/api/'))return next();
  const origins=new Set([`http://127.0.0.1:${port}`,`http://localhost:${port}`]);
  if(!origins.has('http://'+req.headers.host)||(req.headers.origin&&!origins.has(req.headers.origin))||req.headers['sec-fetch-site']==='cross-site'){res.writeHead(403);res.end('仅允许本机页面访问');return;}
  if(!token){res.writeHead(503);res.end('本机服务配置未加载');return;}next();
 });}}],
 server:{host:'127.0.0.1',port,strictPort:true,fs:{strict:true},proxy:{'/api/transmission':proxy('http://127.0.0.1:18087'),'/api/qualification':proxy('http://127.0.0.1:18087'),'/api/macro-changes':proxy('http://127.0.0.1:18087'),'/api/practical':proxy('http://127.0.0.1:18087'),'/api':proxy('http://127.0.0.1:18080'),'/actuator':'http://127.0.0.1:18087'}},
 build:{outDir:'dist-development',emptyOutDir:true}
});
