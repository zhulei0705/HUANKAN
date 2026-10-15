import {test,expect} from '@playwright/test';
import {writeFileSync} from 'node:fs';
test('全部产品路由390px中文标题、可读字号与溢出检查',async({page})=>{
 test.setTimeout(120000);await page.setViewportSize({width:390,height:844});
 const errors:string[]=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:5176/terminal.html#/official');await expect(page.locator('.health-card')).toHaveCount(9);
 const routes=['overview','live','official','macro','companies','industry','linkage','research','radar','risk','evidence','sources','settings','brief','facts','leads','important','associations','review'];const results=[];
 for(const route of routes){await page.evaluate(route=>location.hash='/'+route,route);await page.waitForTimeout(1800);await expect(page.locator('h1')).toHaveCount(1);const result=await page.evaluate(()=>({route:location.hash,title:document.querySelector('h1')!.textContent,overflow:document.documentElement.scrollWidth>innerWidth,smallText:[...document.querySelectorAll('main p,main button,main label,main summary')].filter(e=>e.getBoundingClientRect().width>0&&e.textContent?.trim()&&parseFloat(getComputedStyle(e).fontSize)<13).map(e=>e.textContent?.slice(0,80))}));expect(result.title).toMatch(/[\u3400-\u9fff]/);expect(result.overflow).toBe(false);expect(result.smallText).toEqual([]);results.push(result);}
 writeFileSync('../docs/evidence/s62-regression-s55-all-mobile-audit.json',JSON.stringify({capturedAt:new Date().toISOString(),results,errors},null,2));expect(errors).toEqual([]);
});

// Pace acceptance requests within the existing 120/minute service budget.
test.beforeEach(async({page})=>{await page.waitForTimeout(8000);});


