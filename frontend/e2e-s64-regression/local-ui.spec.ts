import {fileURLToPath} from 'node:url';
import {test,expect} from '@playwright/test';
test('中文初始状态不展示虚假金融事件',async({page})=>{
 await page.goto('/');
 await expect(page).toHaveTitle(/寰瞰/);
 await expect(page.getByRole('heading',{name:'全球实时情报',exact:true})).toBeVisible();await expect(page.locator('.connection')).toContainText('已连接');
 await expect(page.locator('input[type=password]')).toHaveCount(0);
 await expect(page.locator('article[data-event-id]')).not.toHaveCount(0);await expect(page.locator('main')).not.toContainText('【隔离测试】');
 await page.getByRole('button',{name:'数据源监控'}).click();
 await expect(page.getByRole('heading',{name:'数据源监控',exact:true})).toBeVisible();
 await expect(page.locator('html')).toHaveAttribute('lang','zh-CN');
 await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s64-closure-regression-s62-regression-s55-unified-regression-s30-regression-browser-desktop.png',import.meta.url)),fullPage:true});
});
test('移动端导航和连接界面不横向溢出',async({page})=>{
 await page.setViewportSize({width:390,height:844});await page.goto('/');
 await expect(page.getByRole('heading',{name:'全球实时情报',exact:true})).toBeVisible();await expect(page.locator('.connection')).toContainText('已连接');
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
 await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s64-closure-regression-s62-regression-s55-unified-regression-s30-regression-browser-mobile.png',import.meta.url)),fullPage:true});
});


// Respect the unchanged shared API request budget during the full browser regression.
test.afterEach(async()=>{await new Promise(resolve=>setTimeout(resolve,15000));});

// Pace acceptance requests within the existing 120/minute service budget.
test.beforeEach(async({page})=>{await page.waitForTimeout(8000);});

