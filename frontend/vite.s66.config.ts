import {defineConfig,type ProxyOptions} from 'vite';
import vue from '@vitejs/plugin-vue';
import {uiProjection} from './uiProjection';
const token=process.env.HUANKAN_ADMIN_TOKEN;
const proxy=(target:string):ProxyOptions=>({target,configure(server){server.on('proxyReq',r=>{r.removeHeader('authorization');if(token)r.setHeader('Authorization','Bearer '+token);});}});
export default defineConfig({plugins:[{name:'s64-auto-connection',enforce:'pre',transform(code,id){if(id.endsWith('/src/App.vue'))return code.replace("['5173','5176']","['5173','5176','5180']");}},vue(),uiProjection(),{name:'s64-local-access',configureServer(server){server.middlewares.use((req,res,next)=>{if(!req.url?.startsWith('/api/'))return next();const allowed=new Set(['http://127.0.0.1:5180','http://localhost:5180']);if(!allowed.has('http://'+req.headers.host)||(req.headers.origin&&!allowed.has(req.headers.origin))||req.headers['sec-fetch-site']==='cross-site'){res.writeHead(403);res.end('仅允许本机页面访问');return;}if(!token){res.writeHead(503);res.end('本机服务配置未加载');return;}next();});}}],server:{host:'127.0.0.1',port:5180,proxy:{'/api/transmission':proxy('http://127.0.0.1:18087'),'/api/qualification':proxy('http://127.0.0.1:18087'),'/api/macro-changes':proxy('http://127.0.0.1:18087'),'/api/practical':proxy('http://127.0.0.1:18087'),'/api':proxy('http://127.0.0.1:18080'),'/actuator':'http://127.0.0.1:18087'}}});


