import {chromium} from '../frontend/node_modules/playwright/index.mjs';
import {writeFileSync} from 'node:fs';
const seconds=Number(process.argv[2]||3600);if(!Number.isInteger(seconds)||seconds<30||seconds>86400)throw new Error('观测时长须为30至86400秒');
const output=process.argv[3]||'docs/evidence/s23-online-browser-observation-v2.json';if(!/^docs\/evidence\/[a-z0-9-]+\.json$/.test(output))throw new Error('观测输出路径无效');
const report={started_at:new Date().toISOString(),requested_seconds:seconds,clients:2,samples:[],complete:false};
const save=()=>writeFileSync(output,JSON.stringify(report,null,2));
const browser=await chromium.launch({headless:true});
const pages=[];
try{
 for(let i=0;i<2;i++){const context=await browser.newContext({viewport:{width:i?390:1280,height:i?844:900}});const page=await context.newPage();pages.push(page);await page.goto('http://127.0.0.1:5173');await page.getByPlaceholder('访问密钥').fill(process.env.HUANKAN_ADMIN_TOKEN);await page.getByRole('button',{name:'连接服务',exact:true}).click();await page.locator('.connection').filter({hasText:'已连接'}).waitFor({timeout:60000});}
 save();console.log('两个真实浏览器客户端已连接，等待官方新增；不写入金融测试数据。');
 const start=Date.now();while(Date.now()-start<seconds*1000){try{const pageStates=[];for(const page of pages){if(await page.getByPlaceholder('访问密钥').isVisible()){await page.getByPlaceholder('访问密钥').fill(process.env.HUANKAN_ADMIN_TOKEN);await page.getByRole('button',{name:'连接服务',exact:true}).click();}pageStates.push(await page.locator('.connection').innerText());}const response=await fetch('http://127.0.0.1:18080/api/stream/browser/status',{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:'{}',signal:AbortSignal.timeout(15000)});if(!response.ok)throw new Error('HTTP '+response.status);report.samples.push({time:new Date().toISOString(),pageStates,...await response.json()});}catch(e){report.samples.push({time:new Date().toISOString(),error:String(e)});}save();await new Promise(resolve=>setTimeout(resolve,15000));}
 report.complete=true;report.ended_at=new Date().toISOString();report.actual_seconds=(Date.now()-start)/1000;save();
}finally{await browser.close();save();}
