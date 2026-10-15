import {chromium} from '../frontend/node_modules/playwright/index.mjs';import {appendFileSync,writeFileSync,existsSync} from 'node:fs';import {api} from './s46-db.mjs';
const seconds=Number(process.argv[2]||86400),name=process.argv[3]||'s69-online-current';
if(!Number.isInteger(seconds)||seconds<30||seconds>86400||!/^s69-[a-z0-9-]+$/.test(name))throw Error('观察参数无效');
const output=`docs/evidence/${name}.json`,trace=`docs/evidence/${name}.jsonl`;if(existsSync(output)||existsSync(trace))throw Error('不得覆盖已有观察证据');
const report={started_at:new Date().toISOString(),requested_seconds:seconds,complete:false,browser_instances:2,engine:'Chromium',samples:[],errors:[]};
const save=()=>writeFileSync(output,JSON.stringify(report,null,2));const record=data=>appendFileSync(trace,JSON.stringify({recorded_at:new Date().toISOString(),...data})+'\n');
const browsers=[],pages=[];
try{
 for(let client=0;client<2;client++){
  const browser=await chromium.launch({headless:true,args:['--enable-precise-memory-info']});browsers.push(browser);const page=await browser.newPage({viewport:{width:client?390:1920,height:client?844:1080}});pages.push(page);
  page.on('pageerror',e=>record({client,type:'page-error',message:e.message}));
  page.on('response',async r=>{const path=new URL(r.url()).pathname;try{if(path==='/api/stream'){record({client,type:'sse-open',http:r.status(),connection_id:r.headers()['x-stream-connection-id'],last_event_id:r.request().headers()['last-event-id']});}else if(['/api/stream/browser/ack','/api/stream/ack-batch','/api/stream/close'].includes(path)){record({client,type:path,http:r.status(),receipts:r.request().postDataJSON()});}}catch(e){record({client,type:'trace-error',message:String(e)});}});
  page.on('requestfailed',r=>{if(new URL(r.url()).pathname.startsWith('/api/stream'))record({client,type:'stream-request-failed',path:new URL(r.url()).pathname,error:r.failure()?.errorText});});
  await page.goto('http://127.0.0.1:5173/#/live');await page.locator('.connection').filter({hasText:'已连接'}).waitFor({timeout:60000});await page.getByLabel('自动滚动').check();await page.locator('[data-event-id]').first().scrollIntoViewIfNeeded();record({client,type:'browser-connected'});
 }
 const started=Date.now();save();console.log('两个独立浏览器实例在线；只观察真实官方消息，不写Fixture。');
 while(Date.now()-started<seconds*1000){try{const clients=[];for(const [client,page] of pages.entries()){const viewport=page.locator('.virtual-events');const visible=await page.locator('[data-event-id]').first().evaluate(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&r.top<innerHeight&&r.bottom>0;});if(!visible){await page.getByLabel('自动滚动').check();await viewport.scrollIntoViewIfNeeded();record({client,type:'viewport-restored'});}clients.push(await page.evaluate(()=>({connection:document.querySelector('.connection')?.textContent,visible:document.visibilityState,rendered_events:[...document.querySelectorAll('[data-event-id]')].filter(e=>{const r=e.getBoundingClientRect();return r.width>0&&r.height>0&&r.top<innerHeight&&r.bottom>0;}).map(e=>e.getAttribute('data-event-id')),heap_bytes:(performance).memory?.usedJSHeapSize??null})));}const result=await api('stream/browser/status');report.samples.push({at:new Date().toISOString(),clients,status:result});}catch(e){report.errors.push({at:new Date().toISOString(),error:String(e)});}save();await new Promise(r=>setTimeout(r,15000));}
 report.complete=true;report.finished_at=new Date().toISOString();save();
}catch(e){report.errors.push({at:new Date().toISOString(),error:String(e)});process.exitCode=1;}finally{for(const b of browsers)await b.close();save();}
