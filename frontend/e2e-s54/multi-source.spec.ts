import {fileURLToPath} from 'node:url';
import {test,expect} from '@playwright/test';
test('真实多来源筛选、监控、聚合等级与证据在桌面和手机显示',async({page})=>{
 const token=process.env.HUANKAN_ADMIN_TOKEN;if(!token)throw new Error('本地访问密钥未配置');
 await page.goto('http://127.0.0.1:5173');await expect(page.locator('.connection')).toContainText('已连接');
 await page.getByLabel('自动更新列表').uncheck();
 for(const source of ['ECB','GDELT']){
  await page.getByLabel('筛选来源').selectOption(source);const responsePromise=page.waitForResponse(r=>r.url().endsWith('/api/events/query')&&r.request().postDataJSON().source===source);await page.getByRole('button',{name:'查询',exact:true}).click();const response=await responsePromise;expect(response.ok()).toBe(true);const actual=await response.json();expect(actual.total).toBeGreaterThan(0);await expect(page.locator('article[data-event-id]').first()).toBeVisible();expect(await page.locator('article[data-event-id]').count()).toBeLessThanOrEqual(actual.items.length);await expect(page.locator('footer')).toContainText('共 '+actual.total+' 条');
  if(source==='ECB'){await expect(page.locator('article[data-event-id]').first()).toContainText('欧洲央行');await expect(page.locator('article[data-event-id]').first().getByRole('link',{name:'查看原始来源'})).toHaveAttribute('href',/^https:\/\/www\.ecb\.europa\.eu\//);}
  else {await expect(page.locator('article[data-event-id]').first()).toContainText('聚合线索');await expect(page.locator('article[data-event-id]').first()).toContainText('待核验');await expect(page.locator('article[data-event-id]').first()).toContainText('发布时间未知');await page.locator('article[data-event-id]').first().getByRole('button',{name:'查看证据'}).click();await expect(page.getByText('GDELT 批次',{exact:false})).toBeVisible();const first=await page.locator('article[data-event-id]').first().getAttribute('data-event-id');await page.locator('.virtual-events').evaluate(el=>el.scrollTop=el.scrollHeight);await expect(page.locator(`article[data-event-id="${first}"]`)).toHaveCount(0);expect(await page.locator('article[data-event-id]').count()).toBeLessThan(50);await page.locator('.virtual-events').evaluate(el=>el.scrollTop=0);}
 }
 await page.setViewportSize({width:390,height:844});expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s62-regression-s55-unified-regression-s30-regression-s-multi-source-mobile.png',import.meta.url))});
 await page.getByRole('button',{name:'展开导航'}).click();await page.getByRole('button',{name:'数据源监控',exact:true}).click();await expect(page.getByRole('heading',{name:'数据源健康列表'})).toBeVisible();await expect(page.locator('main')).toContainText('尚无真实新增样本');await page.setViewportSize({width:1440,height:900});await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s62-regression-s55-unified-regression-s30-regression-s-source-monitor-desktop.png',import.meta.url))});
});



// Respect the unchanged shared API request budget during the full browser regression.
test.afterEach(async()=>{await new Promise(resolve=>setTimeout(resolve,15000));});

// Pace acceptance requests within the existing 120/minute service budget.
test.beforeEach(async({page})=>{await page.waitForTimeout(8000);});
