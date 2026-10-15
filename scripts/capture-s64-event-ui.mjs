import {chromium} from '../frontend/node_modules/playwright/index.mjs';
import {writeFileSync} from 'node:fs';
const browser=await chromium.launch({headless:true});const records=[];
try{for(const width of [1920,390]){
 const page=await browser.newPage({viewport:{width,height:width===390?844:1080}});
 await page.goto('http://127.0.0.1:5178/#/important');await page.getByText('查询截止：').waitFor({timeout:60000});
 const event=page.locator('article[data-event-id]').first();await event.waitFor();await event.evaluate(e=>e.scrollIntoView({block:'start'}));
 const file=`docs/evidence/s64-closure-${width}-event.png`;await page.screenshot({path:file});
 records.push({at:new Date().toISOString(),width,height:width===390?844:1080,file,event_id:await event.getAttribute('data-event-id'),text:await event.innerText(),horizontal_overflow:await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2)});
 await page.close();
}}finally{await browser.close();}
writeFileSync('docs/evidence/s64-closure-event-screenshot-times.json',JSON.stringify(records,null,2));console.log(JSON.stringify(records.map(({text,...r})=>r)));
