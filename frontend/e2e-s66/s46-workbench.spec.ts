import {test,expect} from '@playwright/test';import {fileURLToPath} from 'node:url';
test('金融研究雷达展示真实目录、资格拒绝原因、BLS预算和未配置模型，兼容手机',async({page})=>{
 test.setTimeout(180000);const token=process.env.HUANKAN_ADMIN_TOKEN;if(!token)throw Error('本地访问密钥未配置');await page.goto('/');await expect(page.locator('.connection')).toContainText('已连接');await page.getByRole('button',{name:'智能机会雷达',exact:false}).click();await expect(page.getByRole('heading',{name:'用户关注与金融研究雷达'})).toBeVisible();await page.getByLabel('关注类型').selectOption('INDICATOR');const catalog=await page.request.post('/api/watch/catalog',{headers:{Authorization:'Bearer '+token},data:{}});expect(catalog.ok()).toBe(true);await expect(page.getByLabel('关注目标').locator('option')).toHaveCount(1+(await catalog.json()).indicators.length);await expect(page.getByLabel('关注目标')).toContainText('EXR.');
 await page.getByRole('button',{name:'消息资格审计',exact:true}).click();await expect(page.locator('.workbench')).toContainText('历史回填',{timeout:60000});await expect(page.locator('.workbench')).toContainText('只读历史重算');await expect(page.locator('.workbench')).toContainText('部分批次已提交');
 await page.getByRole('button',{name:'美国劳工统计局（BLS）官方数据',exact:true}).click();await expect(page.locator('.workbench')).toContainText('CUUR0000SA0');await expect(page.locator('.workbench')).toContainText('25');await expect(page.getByRole('link',{name:'查看官方序列定义'}).first()).toHaveAttribute('href',/^https:\/\/data\.bls\.gov\/timeseries\/[A-Z0-9]+$/);
 await page.getByRole('button',{name:'宏观研究',exact:true}).click();await expect(page.getByLabel('研究指标')).toContainText('EXR.');await expect(page.locator('.workbench')).toContainText('不计算超预期');
 await page.getByRole('button',{name:'人工智能（AI）评测准备',exact:true}).click();await expect(page.locator('.workbench')).toContainText('尚未配置');await expect(page.locator('.workbench')).toContainText('真实执行 0');await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s66-regression-s62-regression-s55-unified-workbench-desktop.png',import.meta.url))});
 await page.setViewportSize({width:390,height:844});await page.locator('.workbench').scrollIntoViewIfNeeded();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:fileURLToPath(new URL('../../docs/evidence/s66-regression-s62-regression-s55-unified-workbench-mobile.png',import.meta.url))});
});test.afterEach(async()=>{await new Promise(r=>setTimeout(r,15000));});

// Pace acceptance requests within the existing 120/minute service budget.
test.beforeEach(async({page})=>{await page.waitForTimeout(8000);});



