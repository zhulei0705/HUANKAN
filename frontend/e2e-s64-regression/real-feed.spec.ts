import {fileURLToPath} from 'node:url';
import {test,expect} from '@playwright/test';
test('真实历史RSS可查询证据，两个浏览器连接不提醒历史回填',async({browser})=>{
 test.setTimeout(60000);
 const token=process.env.HUANKAN_ADMIN_TOKEN;if(!token)throw new Error('必须配置真实本地服务访问密钥');
 const a=await browser.newContext();const b=await browser.newContext();
 try {
  for(const context of [a,b]){
   const page=await context.newPage();await page.goto('http://127.0.0.1:5173');
   await expect(page.locator('.connection')).toContainText('已连接',{timeout:15000});await expect(page.locator('article[data-event-id]').first()).toBeVisible();
   const historical=page.locator('article[data-event-id]').filter({hasText:'历史回填'}).first();
   await expect(historical).toBeVisible();await expect(historical).toContainText('实际采集');
   await historical.getByRole('button',{name:'查看证据'}).click();await expect(page.locator('code').filter({hasText:'SHA-256'}).first()).toBeVisible();
   const official=historical.getByRole('link',{name:'查看原始来源'});await expect(official).toHaveAttribute('href',/^https:\/\/www\.(federalreserve\.gov|ecb\.europa\.eu|stats\.gov\.cn)\//);
   expect(await page.locator('article[data-event-id]').count()).toBeLessThanOrEqual(50);
   await expect(page.getByRole('button',{name:/条更新通知/})).toHaveCount(0);
  }
  await a.pages()[0].screenshot({path:fileURLToPath(new URL('../../docs/evidence/s64-closure-regression-s62-regression-s55-unified-regression-s30-regression-stage2-real-desktop.png',import.meta.url)),fullPage:false});
  await b.pages()[0].setViewportSize({width:390,height:844});
  await b.pages()[0].evaluate(()=>new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve()))));
  const mobileEvent=b.pages()[0].locator('.virtual-events article[data-event-id]').first();
  await mobileEvent.scrollIntoViewIfNeeded();await expect(mobileEvent).toBeInViewport();
  await b.pages()[0].screenshot({path:fileURLToPath(new URL('../../docs/evidence/s64-closure-regression-s62-regression-s55-unified-regression-s30-regression-stage2-real-mobile.png',import.meta.url)),fullPage:false});
  expect(await b.pages()[0].evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 }finally{await a.close();await b.close();}
});



// Respect the unchanged shared API request budget during the full browser regression.
test.afterEach(async()=>{await new Promise(resolve=>setTimeout(resolve,15000));});

// Pace acceptance requests within the existing 120/minute service budget.
test.beforeEach(async({page})=>{await page.waitForTimeout(8000);});

