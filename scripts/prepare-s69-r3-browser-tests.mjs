import fs from 'node:fs';import path from 'node:path';import {pathToFileURL} from 'node:url';
const root=path.resolve(import.meta.dirname,'..'),candidate=JSON.parse(fs.readFileSync(path.join(root,'runtime/s69-r1-candidate.json'))),work=candidate.work;
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),dir=path.join(work,'e2e-r3');fs.mkdirSync(dir,{recursive:true});
let push=fs.readFileSync(path.join(root,'frontend/e2e-s66-isolated/push.spec.ts'),'utf8').replaceAll('http://127.0.0.1:5177','http://127.0.0.1:5184').replaceAll('isolated-s66-web-test-token-32-characters','isolated-s69-r1-test-token-32-characters').replace('^web_it_','^r1_web_').replace('../../docs/evidence/s66-isolated-s62-isolated-sse.png',pathToFileURL(path.join(root,`docs/evidence/s69-r3-sse-${stamp}.png`)).href);
fs.writeFileSync(path.join(dir,'isolated-sse.spec.ts'),push);
push=push.replace('async({browser})=>{','async({browser})=>{\n const initial=Number(sql("SELECT count(*) FROM financial_event;").trim().split(/\\r?\\n/).at(-1));').replaceAll("'共 253 条'","'共 '+(initial+253)+' 条'");
fs.writeFileSync(path.join(dir,'isolated-sse.spec.ts'),push);
const screenshots=path.join(root,'docs/evidence');
fs.writeFileSync(path.join(dir,'isolated-watch-evidence.spec.ts'),`import {test,expect} from '@playwright/test';
test('独立静态页面关注增删与证据详情真实回读',async({page})=>{
 await page.goto('http://127.0.0.1:5184/#/live');await page.getByPlaceholder('访问密钥').fill('isolated-s69-r1-test-token-32-characters');await page.getByRole('button',{name:'连接服务',exact:true}).click();await expect(page.locator('.connection')).toContainText('已连接');
 const call=async(path:string,body:any={})=>page.evaluate(async({path,body})=>{const response=await fetch('/api/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});return {http:response.status,data:await response.json()};},{path,body});
 const added=await call('watch/add',{kind:'SOURCE',value:'FED',label:'【隔离测试】美联储',includeLeads:false,minimumPriority:0});expect(added.http).toBe(200);expect(added.data.some((r:any)=>r.value==='FED')).toBe(true);
 await page.getByLabel('按我的关注名单推送').check();await expect(page.locator('.connection')).toContainText('已连接');
 await page.locator('[data-event-id]').first().getByRole('button',{name:'查看证据',exact:true}).click();await expect(page.getByRole('heading',{name:'原始证据版本'})).toBeVisible();await expect(page.getByText('SHA-256',{exact:false}).first()).toBeVisible();
 const id=added.data.find((r:any)=>r.value==='FED').id;expect((await call('watch/remove',{id})).http).toBe(200);expect((await call('watch/query')).data.find((r:any)=>r.id===id).enabled).toBe(false);
 await page.screenshot({path:${JSON.stringify(path.join(screenshots,`s69-r3-watch-evidence-${stamp}.png`))},fullPage:true});
});`);
const routes=[['实时情报','/live','全球实时情报'],['官方发布','/official','全球官方发布监控'],['证据详情','/research?event=c16fd86a-524d-46c5-bb53-5ced3ecb21d1','金融事件研判'],['宏观与S65','/macro?series=EXR.D.USD.EUR.SP00.A&from=2026-10-08&to=2026-10-09','全球宏观监测'],['S66历史研究','/linkage?snapshot=a4da755b-507e-45e5-9362-ece080c53ee0','全球市场联动'],['S64重要事件','/important','全球重要事件'],['来源监控','/sources','数据源监控'],['设置','/settings','系统设置']];
fs.writeFileSync(path.join(dir,'static-routes.spec.ts'),`import {test,expect} from '@playwright/test';
const routes=${JSON.stringify(routes)};
for(const width of [1920,390])for(const [name,route,title] of routes)test('冻结静态直接访问与刷新 '+name+' '+width,async({page})=>{
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));await page.setViewportSize({width,height:width===390?844:1080});await page.goto('http://127.0.0.1:5182/#'+route);await expect(page.locator('.connection')).toContainText('已连接',{timeout:60000});await expect(page.locator('h1')).toHaveText(title);await page.reload();await expect(page.locator('h1')).toHaveText(title);await expect(page.locator('.connection')).toContainText('已连接',{timeout:60000});
 if(route.startsWith('/macro')){await expect(page.locator('.macro-change')).toContainText('全球官方宏观变化分析',{timeout:60000});await expect(page.getByRole('link',{name:'研究影响传导'}).first()).toBeVisible();}
 if(route.startsWith('/linkage'))await expect(page.locator('.transmission')).toContainText('0.0020',{timeout:60000});
 if(route.startsWith('/research?'))await expect(page.getByRole('heading',{name:'金融事件研究详情',exact:true})).toBeVisible({timeout:60000});
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2)).toBe(true);expect(errors).toEqual([]);
 if(width===390&&['/macro','/linkage'].some(x=>route.startsWith(x)))await page.screenshot({path:${JSON.stringify(path.join(screenshots,`s69-r3-route-${stamp}-`))}+name+'.png',fullPage:true});
 // Respect the unchanged shared 120/min API limit and the existing observation clients.
 await page.waitForTimeout(35000);
});`);
const report=path.join(root,`docs/evidence/s69-r3-browser-${stamp}.json`),log=path.join(root,`runtime/s69-r3-browser-${stamp}.log`);
fs.writeFileSync(path.join(work,'playwright.r3.config.ts'),`import {defineConfig} from '@playwright/test';export default defineConfig({testDir:'./e2e-r3',workers:1,timeout:90000,expect:{timeout:20000},use:{headless:true},reporter:[['list'],['json',{outputFile:${JSON.stringify(report)}}]]});`);
fs.writeFileSync(path.join(root,'runtime/s69-r3-browser-run.json'),JSON.stringify({stamp,work,report,log},null,2));console.log(JSON.stringify({work,report,log}));

