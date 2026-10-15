import {uiProjection} from './uiProjection';
import {defineConfig, type ProxyOptions} from 'vite';import vue from '@vitejs/plugin-vue';import {resolve} from 'node:path';
// The credential stays in the loopback server process, never in client code.
const localToken=process.env.HUANKAN_ADMIN_TOKEN;
const origins=new Set(['http://127.0.0.1:5176','http://localhost:5176']);
const proxy=(target:string):ProxyOptions=>({target,configure(server){server.on('proxyReq',request=>{request.removeHeader('authorization');if(localToken)request.setHeader('Authorization','Bearer '+localToken);});}});
export default defineConfig({plugins:[vue(),uiProjection(),{name:'local-api-access',configureServer(server){server.middlewares.use((request,response,next)=>{
 if(!request.url?.startsWith('/api/'))return next();
 const origin=request.headers.origin;
 if(!origins.has('http://'+request.headers.host)||(origin&&!origins.has(origin))||request.headers['sec-fetch-site']==='cross-site'){
  response.writeHead(403,{'Content-Type':'application/json'});response.end(JSON.stringify({message:'仅允许本机页面访问'}));return;
 }
 if(!localToken){response.writeHead(503,{'Content-Type':'application/json'});response.end(JSON.stringify({message:'本地服务配置未加载，请使用项目启动脚本启动研究页面'}));return;}
 next();
});}}],server:{host:'127.0.0.1',port:5176,strictPort:true,proxy:{'/api/practical':proxy('http://127.0.0.1:18083'),'/api':proxy('http://127.0.0.1:18080')}},build:{outDir:'dist-s54',rollupOptions:{input:{legacy:resolve(import.meta.dirname,'s54.html'),terminal:resolve(import.meta.dirname,'terminal.html')}}}});
