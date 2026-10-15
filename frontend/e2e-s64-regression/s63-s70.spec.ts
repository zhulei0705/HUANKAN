import {test,expect} from '@playwright/test';
import {writeFileSync} from 'node:fs';import {resolve} from 'node:path';
const evidence=(name:string)=>resolve('../docs/evidence/s64-closure-regression-'+name);
test('S64真实事件排序、评分证据、快照分页及空分类',async({page})=>{
 await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});
 await page.getByText('为什么排在这里').first().click();await expect(page.getByText('官方来源等级：').first()).toBeVisible();await expect(page.getByText('输入证据').first()).toBeVisible();
 await page.getByRole('button',{name:'复核此快照'}).click();await expect(page.getByText('冻结输入哈希与评分复算通过',{exact:false})).toBeVisible();const first=await page.locator('code').first().textContent();await page.getByRole('button',{name:'下一页',exact:true}).last().click();await expect(page.getByText('第 2 页')).toBeVisible();expect(await page.locator('code').first().textContent()).toBe(first);
 await page.getByLabel('事件与产业类型').selectOption('光通信与数据中心');await page.getByRole('button',{name:'生成可复核排序'}).click();await expect(page.getByText('范围内暂无真实事件。')).toBeVisible({timeout:60000});
});
test('S67真实详情生命周期只读与真实关注更新状态',async({page,request})=>{
 await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});await page.getByRole('link',{name:'查看研究与历史证据'}).first().click();
 await expect(page.getByRole('heading',{name:'持续关注与事件复盘'})).toBeVisible();await expect(page.getByRole('button',{name:'关注此事件',exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'记录开始研究'})).toBeDisabled();
 // Read-only acceptance: do not insert synthetic formal follow rules or work actions.
 const response=await request.post('/api/watch/updates/query',{data:{before:0}});expect(response.ok()).toBe(true);const actual=await response.json();await page.goto('/#/radar');await expect(page.getByRole('heading',{name:'关注更新与站内提醒'})).toBeVisible();if(actual.length)await expect(page.getByRole('link',{name:'查看事件历史与证据',exact:true})).toHaveCount(actual.length);else await expect(page.getByText('尚无符合关注与通知资格的持久化更新。')).toBeVisible();
});
test('S70重要事件三尺寸与实际证据截图',async({page})=>{
 const results=[];for(const [width,height] of [[1920,1080],[1366,768],[390,844]]){
  await page.setViewportSize({width,height});await page.goto('/#/important');await expect(page.getByText('查询截止：')).toBeVisible({timeout:60000});
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth+2);expect(overflow).toBe(false);
  const filename=`s70-final-important-${width}.png`;await page.screenshot({path:evidence(filename),fullPage:false});results.push({width,height,at:new Date().toISOString(),filename,overflow});
 }writeFileSync(evidence('s70-final-important-screenshots.json'),JSON.stringify(results,null,2));
});

test('S65—S68真实ECB观测研究、持久化关注、工作通知与补读',async({page,request})=>{
 const response=await request.post('/api/practical/cards',{data:{date:'',source:'ECB_DATA',topic:'',watchOnly:false,page:0}});expect(response.ok()).toBe(true);const cards=await response.json();const event=cards.find((r:any)=>r.series_id&&r.facts?.some((f:any)=>f.status==='FIELD_MATCHED'));expect(event).toBeTruthy();
 const comparison=await request.post('/api/practical/macro/analyze',{data:{series:event.series_id,asOf:new Date().toISOString()}});expect(comparison.ok()).toBe(true);const frozen=await comparison.json();expect(frozen.rule_version).toBe('宏观同口径比较-1.1');expect(frozen.report.fact_summary.market_expectation).toBeNull();expect(frozen.report.fact_summary.current.evidence_id).toBeTruthy();
 await page.goto('/#/research?event='+event.id);await expect(page.getByRole('heading',{name:'持续关注与事件复盘'})).toBeVisible();await page.getByRole('button',{name:'生成证据约束的影响研究'}).first().click();await expect(page.getByText('规则研判-1.1',{exact:false})).toBeVisible();
 await page.getByRole('button',{name:'关注此事件',exact:true}).click();await expect(page.getByRole('button',{name:'取消事件关注'})).toBeVisible();await page.reload();await expect(page.getByRole('button',{name:'取消事件关注'})).toBeVisible();
 await page.getByLabel('实际研究动作说明').fill('S70实际复核：核对当前官方观测与原始证据版本');await page.getByRole('button',{name:'记录开始研究'}).click();await expect(page.getByText('S70实际复核：核对当前官方观测与原始证据版本',{exact:false}).first()).toBeVisible();
 await page.goto('/#/radar');await expect(page.getByRole('heading',{name:'个人研究工作状态更新'}).first()).toBeVisible();await page.getByRole('button',{name:'标记已读',exact:true}).first().click();await page.reload();await expect(page.getByText('已读',{exact:false}).first()).toBeVisible();
 await page.goto('/#/research?event='+event.id);await expect(page.getByRole('button',{name:'取消事件关注'})).toBeVisible();await page.getByRole('button',{name:'取消事件关注'}).click();await expect(page.getByRole('button',{name:'关注此事件',exact:true})).toBeVisible();
 writeFileSync(evidence('s70-real-workflow.json'),JSON.stringify({at:new Date().toISOString(),event_id:event.id,evidence_id:event.evidence_id,series_id:event.series_id,macro_snapshot_id:frozen.id,rule_version:frozen.rule_version,action:'实际官方观测核查，临时关注已取消；个人通知不计官方LIVE'},null,2));
});

