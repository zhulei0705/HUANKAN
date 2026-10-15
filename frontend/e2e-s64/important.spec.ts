import {test,expect} from '@playwright/test';
import {writeFileSync} from 'node:fs';import {resolve} from 'node:path';
test('S64真实统计、来源筛选、回填解释与新旧规则复算',async({page,request})=>{
 await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});await expect(page.getByRole('region',{name:'真实事件概况'})).toBeVisible();
 await page.getByRole('combobox',{name:'官方来源',exact:true}).selectOption('NBS');await page.getByRole('button',{name:'生成可复核排序'}).click();await expect(page.getByRole('button',{name:'生成可复核排序'})).toBeEnabled({timeout:60000});await expect(page.getByText('规则 重要事件研究排序-1.1',{exact:false})).toBeVisible();
 const articles=page.locator('.feed article[data-event-id]');await expect(articles.first()).toBeVisible();for(const a of await articles.all()){await expect(a).toContainText('国家统计局');await expect(a).not.toContainText('GDELT');}
 await articles.first().getByText('为什么排在这里').click();await expect(articles.first().getByText('官方发布时间新近程度：0分')).toBeVisible();await articles.first().getByText('实际评分输入').click();await expect(articles.first().locator('pre')).toContainText('HISTORICAL_BACKFILL');
 await page.getByRole('button',{name:'复核此快照'}).click();await expect(page.getByText('冻结输入哈希与评分复算通过',{exact:false})).toBeVisible();
 const rank=await request.post('/api/practical/priority/rank',{data:{officialOnly:true,source:'GDELT',minimum:0}});expect(rank.ok()).toBe(true);expect((await rank.json()).matched).toBe(0);
});
test('S64列表关注持久化并恢复原状态',async({page})=>{
 await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});const first=page.locator('article[data-event-id]').first();const id=await first.getAttribute('data-event-id');const initiallyFollowed=await first.getByRole('button',{name:'取消事件关注',exact:true}).count();
 if(initiallyFollowed)return;try{await first.getByRole('button',{name:'关注此事件',exact:true}).click();await expect(first.getByRole('button',{name:'取消事件关注'})).toBeVisible();await page.reload();await expect(page.locator(`article[data-event-id="${id}"]`).getByRole('button',{name:'取消事件关注'})).toBeVisible({timeout:60000});}finally{const target=page.locator(`article[data-event-id="${id}"]`);await target.getByRole('button',{name:'取消事件关注'}).click();await expect(target.getByRole('button',{name:'关注此事件',exact:true})).toBeVisible();}
});
test('S64桌面与390px手机真实数据截图、原文与空状态',async({page})=>{
 const records=[];for(const [width,height] of [[1920,1080],[390,844]]){await page.setViewportSize({width,height});await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});await expect(page.getByRole('region',{name:'真实事件概况'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+2)).toBe(true);const filename=`s64-closure-${width}.png`;await page.screenshot({path:resolve('../docs/evidence/'+filename)});records.push({width,height,at:new Date().toISOString(),filename});}
 const first=page.locator('article[data-event-id]').first();await first.getByText('查看原文标题').click();await expect(first.locator('details').last()).not.toBeEmpty();await page.getByText('筛选重要事件',{exact:true}).click();await page.getByLabel('事件与产业类型').selectOption('光通信与数据中心');await page.getByRole('button',{name:'生成可复核排序'}).click();await expect(page.getByText('范围内暂无真实事件。')).toBeVisible({timeout:60000});writeFileSync(resolve('../docs/evidence/s64-closure-screenshot-times.json'),JSON.stringify(records,null,2));
});
