import {fileURLToPath} from 'node:url';
import {test,expect} from '@playwright/test';
test('中文初始状态不展示虚假金融事件',async({page})=>{
 await page.goto('/');
 await expect(page).toHaveTitle(/寰瞰/);
 await expect(page.getByRole('heading',{name:'连接本地情报服务'})).toBeVisible();
 await expect(page.getByRole('button',{name:'连接服务',exact:true})).toBeDisabled();
 await expect(page.locator('article')).toHaveCount(0);
 await page.getByRole('button',{name:'数据源监控'}).click();
 await expect(page.getByRole('heading',{name:'数据源监控',exact:true})).toBeVisible();
 await expect(page.locator('html')).toHaveAttribute('lang','zh-CN');
 await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s46-regression-s30-regression-browser-desktop.png',import.meta.url)),fullPage:true});
});
test('移动端导航和连接界面不横向溢出',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/');
 await expect(page.getByRole('heading',{name:'连接本地情报服务'})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s46-regression-s30-regression-browser-mobile.png',import.meta.url)),fullPage:true});
});


// Respect the unchanged shared API request budget during the full browser regression.
test.afterEach(async()=>{await new Promise(resolve=>setTimeout(resolve,15000));});
