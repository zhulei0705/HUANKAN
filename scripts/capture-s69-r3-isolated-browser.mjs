import fs from 'node:fs';import {execFileSync} from 'node:child_process';
const test=JSON.parse(fs.readFileSync('runtime/s69-r3-test-process.json','utf8').replace(/^\uFEFF/,''));
if(process.env.HUANKAN_TEST_DB_URL!=='jdbc:postgresql://127.0.0.1:55432/huankan_test'||!/^r1_web_[a-f0-9]{32}$/.test(test.schema))throw Error('拒绝访问非隔离测试目标');
const query=`BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SET LOCAL search_path TO ${test.schema};
 SELECT json_build_object('at',clock_timestamp(),'database',current_database(),'port',inet_server_port(),'schema',current_schema(),
 'evidence',(SELECT count(*) FROM raw_evidence),'outbox',(SELECT count(*) FROM stream_outbox),
 'outbox_duplicate_groups',(SELECT count(*) FROM(SELECT evidence_id,event_id,notification_kind FROM stream_outbox GROUP BY evidence_id,event_id,notification_kind HAVING count(*)>1)x),
 'hash_errors',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')),
 'sent',(SELECT count(*) FROM stream_delivery WHERE outcome='SENT'),'failed_delivery',(SELECT count(*) FROM stream_delivery WHERE outcome='FAILED'),
 'browser_observations',(SELECT count(*) FROM browser_delivery_observation),
 'live_rendered',(SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id WHERE b.rendered_at IS NOT NULL AND d.delivery_type='LIVE' AND NOT d.replay),
 'replay_rendered',(SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id WHERE b.rendered_at IS NOT NULL AND d.delivery_type='REPLAY' AND d.replay),
 'replay_received',(SELECT count(*) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id WHERE d.delivery_type='REPLAY' AND d.replay),
 'sent_without_browser_receipt',(SELECT count(*) FROM stream_delivery d WHERE d.outcome='SENT' AND NOT EXISTS(SELECT 1 FROM browser_delivery_observation b WHERE b.delivery_id=d.id)),
 'failed_delivery_records',(SELECT coalesce(json_agg(d),'[]'::json) FROM stream_delivery d WHERE outcome='FAILED'),
 'qualification_backlog',(SELECT greatest(coalesce((SELECT max(id) FROM stream_outbox),0)-last_outbox,0) FROM notification_audit_cursor WHERE name='LIVE_RULE_AUDIT'),
 'integrity_failed',(SELECT count(*) FROM evidence_integrity_check WHERE NOT passed)); COMMIT;`;
const output=execFileSync('D:/Program Files/PostgreSQL/18/bin/psql.exe',['-h','127.0.0.1','-p','55432','-U',process.env.HUANKAN_TEST_DB_USER,'-d','huankan_test','-w','-Atq','-v','ON_ERROR_STOP=1','-f','-'],{input:Buffer.from(query,'utf8'),encoding:'utf8',env:{...process.env,PGPASSWORD:process.env.HUANKAN_TEST_DB_PASSWORD,PGCLIENTENCODING:'UTF8'}});
const record=JSON.parse(output);if(record.database!=='huankan_test'||record.port!==55432||record.schema!==test.schema)throw Error('实际实例身份不匹配');
const file='docs/evidence/s69-r3-isolated-browser-database-'+Date.now()+'.json';fs.writeFileSync(file,JSON.stringify({...record,qualification:'仅隔离合成事件，不计正式官方LIVE；浏览器用例为同一Chromium的两个隔离context，短时探针/开发隔离另用两独立Chromium'},null,2),{flag:'wx'});console.log(JSON.stringify({file,...record}));
