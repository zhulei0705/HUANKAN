import {fileURLToPath} from 'node:url';
import {test,expect} from '@playwright/test';
import {execFileSync} from 'node:child_process';
import {randomUUID} from 'node:crypto';
const schema=process.env.HUANKAN_WEB_TEST_SCHEMA;
function sql(query:string){
 if(!schema||!/^web_it_[a-f0-9]{32}$/.test(schema)||process.env.HUANKAN_TEST_DB_URL!=='jdbc:postgresql://127.0.0.1:55432/huankan_test')throw new Error('拒绝向非隔离测试库写入Fixture');
 return execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','55432','-U','huankan_test','-d','huankan_test','-w','-v','ON_ERROR_STOP=1','-At','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_TEST_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:Buffer.from(`SET search_path TO ${schema}; ${query}`,'utf8'),encoding:'utf8'});
}
function insert(title:string,kind:string){
 const event=randomUUID(),evidence=randomUUID(),job=randomUUID();
 sql(`BEGIN; INSERT INTO ingestion_job(id,source_id,status) VALUES('${job}','FED','SUCCESS'); INSERT INTO raw_evidence(id,source_id,source_document_id,source_url,published_at,first_seen_at,raw_content,raw_content_hash,item_fingerprint,content_language,parser_version,revision,verification_status,job_id,ingestion_kind) VALUES('${evidence}','FED','isolated-${event}','https://www.federalreserve.gov/test-only',now(),now(),convert_to('ISOLATED WEB TEST','UTF8'),encode(sha256(convert_to('ISOLATED WEB TEST','UTF8')),'hex'),repeat('1',64),'zh-CN','test-only',1,'PENDING','${job}','${kind}'); INSERT INTO financial_event(id,source_id,document_id,original_title,category,evidence_id,ingestion_kind) VALUES('${event}','FED','isolated-${event}','${title}','测试','${evidence}','${kind}'); INSERT INTO event_evidence VALUES('${event}','${evidence}'); INSERT INTO stream_outbox(event_id,evidence_id,notification_kind) VALUES('${event}','${evidence}','${kind}'); COMMIT;`);return event;
}
test('隔离库真实事务提交经SSE自动更新双客户端、离线补读去重、历史回填不提醒',async({browser})=>{
 sql("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='ISOLATED WEB FIXTURE ONLY' WHERE source_id='FED'");
 const a=await browser.newContext(),b=await browser.newContext();
 const pages=[await a.newPage(),await b.newPage()];
 try {
  for(const page of pages){await page.goto('http://127.0.0.1:5177');await page.getByPlaceholder('访问密钥').fill('isolated-s64-web-test-token-32-characters');await page.getByRole('button',{name:'连接服务',exact:true}).click();await page.getByLabel('推送订阅').selectOption('HISTORY');await expect(page.locator('.connection')).toContainText('已连接');}
  const event=insert('【隔离测试】SSE 自动更新验证','NEW_EVENT');
  for(const page of pages)await expect(page.locator(`article[data-event-id="${event}"]`)).toBeVisible({timeout:10000});
  await expect.poll(()=>Number(sql(`SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id WHERE b.event_id='${event}' AND b.rendered_at IS NOT NULL AND d.delivery_type='LIVE' AND NOT d.replay;`).trim().split(/\r?\n/).at(-1)),{timeout:10000}).toBe(2);
  await pages[0].context().setOffline(true);await expect(pages[0].locator('.connection')).toContainText('实时连接已断开',{timeout:10000});
  const offline=insert('【隔离测试】断线期间持久化消息','NEW_EVENT');
  await expect(pages[1].locator(`article[data-event-id="${offline}"]`)).toBeVisible();
  await pages[1].getByLabel('自动更新列表').uncheck();
  insert('【隔离测试】历史回填不提示','HISTORICAL_BACKFILL');
  await pages[0].context().setOffline(false);await expect(pages[0].locator('.connection')).toContainText('已连接',{timeout:10000});
  await expect(pages[0].locator(`article[data-event-id="${event}"]`)).toHaveCount(1);
  await expect(pages[0].locator(`article[data-event-id="${offline}"]`)).toHaveCount(1);
  await expect.poll(()=>Number(sql(`SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id WHERE b.event_id='${offline}' AND d.delivery_type='REPLAY' AND d.replay;`).trim().split(/\r?\n/).at(-1)),{timeout:10000}).toBeGreaterThanOrEqual(1);
  await expect(pages[1].getByRole('button',{name:/条更新通知/})).toHaveCount(0);
  const bulkJob=randomUUID();
  sql(`BEGIN; INSERT INTO ingestion_job(id,source_id,status) VALUES('${bulkJob}','FED','SUCCESS'); CREATE TEMP TABLE batch_fixture AS SELECT gen_random_uuid() event_id,gen_random_uuid() evidence_id,n FROM generate_series(1,250)n; INSERT INTO raw_evidence(id,source_id,source_document_id,source_url,published_at,first_seen_at,raw_content,raw_content_hash,item_fingerprint,content_language,parser_version,revision,verification_status,job_id,ingestion_kind) SELECT evidence_id,'FED','isolated-bulk-'||event_id,'https://www.federalreserve.gov/test-only','2025-01-01',now(),convert_to('ISOLATED BULK TEST','UTF8'),encode(sha256(convert_to('ISOLATED BULK TEST','UTF8')),'hex'),repeat('2',64),'zh-CN','test-only',1,'PENDING','${bulkJob}','HISTORICAL_BACKFILL' FROM batch_fixture; INSERT INTO financial_event(id,source_id,document_id,original_title,category,evidence_id,ingestion_kind) SELECT event_id,'FED','isolated-bulk-'||event_id,'【隔离测试】历史分页压力 '||n,'测试',evidence_id,'HISTORICAL_BACKFILL' FROM batch_fixture; INSERT INTO event_evidence SELECT event_id,evidence_id FROM batch_fixture; INSERT INTO stream_outbox(event_id,evidence_id,notification_kind) SELECT event_id,evidence_id,'HISTORICAL_BACKFILL' FROM batch_fixture; COMMIT;`);
  await expect(pages[0].getByText('共 253 条',{exact:false})).toBeVisible({timeout:10000});expect(await pages[0].locator('article[data-event-id]').count()).toBeLessThan(50);
  await expect(pages[0].getByText('共 253 条',{exact:false})).toBeVisible();
  await expect(pages[1].getByRole('button',{name:/条更新通知/})).toHaveCount(0);
  await pages[0].screenshot({path:fileURLToPath(new URL('../../docs/evidence/s64-closure-isolated-s62-isolated-sse.png',import.meta.url)),fullPage:true});
 }finally{await Promise.allSettled([a.close(),b.close()]);}
});



