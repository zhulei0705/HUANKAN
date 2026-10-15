import {sql} from './s46-db.mjs';
import {writeFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
const audit=sql(`BEGIN READ ONLY; SELECT jsonb_build_object('at',clock_timestamp(),
'events',(SELECT count(*) FROM financial_event),'official',(SELECT count(*) FROM financial_event e JOIN source_registry s ON s.id=e.source_id WHERE s.level='L0'),
'gdelt',(SELECT count(*) FROM financial_event WHERE source_id='GDELT'),'evidence',(SELECT count(*) FROM raw_evidence),
'clusters',(SELECT count(*) FROM (SELECT DISTINCT r.source_id,CASE WHEN r.source_id='NBS' THEN r.raw_content_hash||'|'||COALESCE(r.published_at::text,'unknown')||'|'||e.original_title ELSE r.source_document_id END FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id)x),
'migrations',(SELECT jsonb_agg(t) FROM (SELECT version,description,checksum,success FROM flyway_schema_history ORDER BY installed_rank)t),
'schema',(SELECT jsonb_agg(t) FROM (SELECT table_name,column_name,data_type FROM information_schema.columns WHERE table_schema='public' AND table_name IN('raw_evidence','financial_event','event_evidence','research_priority_snapshot','user_watch','stream_outbox','watch_update') ORDER BY table_name,ordinal_position)t),
'ingestion',(SELECT jsonb_agg(t) FROM (SELECT ingestion_kind,count(*) FROM raw_evidence GROUP BY ingestion_kind)t),
'fixture',(SELECT count(*) FROM raw_evidence WHERE source_document_id ILIKE '%fixture%' OR source_document_id ILIKE 'isolated-%'),
'gdelt_fact_evidence',(SELECT count(*) FROM fact_evidence f JOIN raw_evidence r ON r.id=f.evidence_id WHERE r.source_id='GDELT')); COMMIT;`);
writeFileSync('docs/evidence/s64-closure-database.json',JSON.stringify(audit,null,2));
console.log(JSON.stringify({at:audit.at,events:audit.events,official:audit.official,gdelt:audit.gdelt,clusters:audit.clusters,migrations:audit.migrations.length,fixture:audit.fixture}));
const request=async(path,body={})=>{const r=await fetch('http://127.0.0.1:18085/api/practical/priority/'+path,{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:JSON.stringify(body),signal:AbortSignal.timeout(120000)});if(!r.ok)throw Error('API '+path+' HTTP '+r.status);return r.json();};
if(process.argv.includes('--ranking')){
 const ranking=await request('rank',{officialOnly:true,minimum:0});
 const reproduction=await request('reproduce',{id:ranking.id});
 const overview=await request('overview');
 const owner=createHash('sha256').update('Bearer '+process.env.HUANKAN_ADMIN_TOKEN).digest('hex');
 const legacy=sql(`SELECT COALESCE((SELECT json_build_object('id',id) FROM research_priority_snapshot WHERE rule_version='重要事件研究排序-1.0' AND owner_key='${owner}' LIMIT 1),'{}'::json)`);
 const legacyReproduction=legacy.id?await request('reproduce',{id:legacy.id}):null;
 writeFileSync('docs/evidence/s64-closure-ranking.json',JSON.stringify({at:new Date().toISOString(),ranking,reproduction,overview,legacyReproduction},null,2));
 console.log(JSON.stringify({snapshot:ranking.id,version:ranking.rule_version,matched:ranking.matched,reproduced:reproduction.reproduced,top:ranking.rows.slice(0,5).map(r=>({id:r.id,title:r.original_title,score:r.score,url:r.source_url}))}));
}
