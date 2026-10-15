import {sql} from './s46-db.mjs';import {writeFileSync} from 'node:fs';
async function api(path,body={}){const r=await fetch('http://127.0.0.1:18083/api/practical/'+path,{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(240000)});return {http:r.status,body:await r.json()};}
const stage=process.argv[2];let result={checked_at:new Date().toISOString(),stage};
if(stage==='permissions')result.review=sql(`UPDATE official_resource_policy SET access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=clock_timestamp(),review_basis=CASE source_id WHEN 'BLS' THEN '2026-10-10核查官方开发者条款与公共统计目录：仅保存有界原始统计响应、系列定义、脚注与数值，注明来源及实际采集日期，无BLS标识或背书；不绕过403' WHEN 'ECB' THEN '2026-10-10核查ECB官网版权条款：免费信息准确引用并注明来源；仅留存公开会议日期和原始HTML证据，不转载署名论文或官方标识' ELSE '2026-10-10核查FED免责声明Copyright/trademark：未另标注信息属于public domain，注明Board来源；仅留存有界日历文本证据，不使用徽标或第三方素材' END WHERE source_id IN('BLS','ECB','FED'); SELECT json_agg(p) FROM official_resource_policy p;`);
if(stage==='diagnose')result.diagnostics=await api('resource/diagnose');
if(stage==='calendars')result.calendars=await api('calendar/sync');
if(stage==='business'){
 result.bulk=await api('bls-file/sync',{first:2025,last:2026});result.bulk_repeat=await api('bls-file/sync',{first:2025,last:2026});
 result.catalog=await api('catalog');result.monitor=await api('monitor');result.sec=await api('sec/preflight');
 result.cards=await api('cards',{date:'2026-10-10',source:'FED',topic:'',watchOnly:false,page:0});
 const event=result.cards.body?.[0];if(event)result.card_detail=await api('card/detail',{id:event.id});
 const series=result.catalog.body?.series?.find(r=>r.id.startsWith('EXR.'));if(series){result.macro=await api('macro',{series:series.id,page:0});result.macro_analysis=await api('macro/analyze',{series:series.id,asOf:new Date().toISOString()});}
 const q={date:'2026-10-10',source:'',topic:'',watchOnly:false,page:0};result.brief=await api('brief/generate',q);result.brief_repeat=await api('brief/generate',q);result.brief_history=await api('brief/query',q);result.industry_empty=await api('brief/generate',{...q,source:'FED',topic:'半导体'});result.personal_empty=await api('brief/generate',{...q,watchOnly:true});
 result.counts=sql(`SELECT json_build_object('raw',(SELECT count(*) FROM raw_evidence),'events',(SELECT count(*) FROM financial_event),'outbox',(SELECT count(*) FROM stream_outbox),'bulk',(SELECT count(*) FROM bls_file_observation),'calendar',(SELECT count(*) FROM official_release_calendar),'brief',(SELECT count(*) FROM financial_brief),'xbrl',(SELECT count(*) FROM sec_xbrl_fact),'migration',(SELECT count(*) FROM flyway_schema_history));`);
}
writeFileSync('docs/evidence/s54-'+stage+'.json',JSON.stringify(result,null,2));console.log(JSON.stringify({stage,httpResults:Object.fromEntries(Object.entries(result).filter(([k,v])=>v?.http).map(([k,v])=>[k,v.http])),counts:result.counts}));
