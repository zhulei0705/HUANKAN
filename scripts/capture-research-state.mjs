import {execFileSync} from 'node:child_process';
import {readFileSync,writeFileSync} from 'node:fs';
const sql=q=>JSON.parse(execFileSync('D:\\Program Files\\PostgreSQL\\18\\bin\\psql.exe',['-h','127.0.0.1','-p','5432','-U',process.env.HUANKAN_DB_USER,'-d','huankan','-w','-At','-q','-v','ON_ERROR_STOP=1','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:Buffer.from(q),encoding:'utf8',maxBuffer:64*1024*1024}));
const baseline=JSON.parse(readFileSync('docs/evidence/s15-baseline.json','utf8'));
const current=sql("SELECT json_agg(t) FROM (SELECT id,raw_content_hash FROM raw_evidence ORDER BY id)t;");const map=new Map(current.map(x=>[x.id,x.raw_content_hash]));
const missing=baseline.evidence.filter(x=>map.get(x.id)!==x.raw_content_hash);if(missing.length)throw new Error('基线证据丢失或变更');
const state=sql(`SELECT json_build_object('checked_at',now(),
 'evidence_count',(SELECT count(*) FROM raw_evidence),'event_count',(SELECT count(*) FROM financial_event),'message_count',(SELECT count(*) FROM financial_event WHERE source_id<>'ECB_DATA'),
 'sources',(SELECT json_agg(t) FROM (SELECT s.id,s.name,s.level,count(DISTINCT e.id) events,count(DISTINCT r.id) evidence FROM source_registry s LEFT JOIN financial_event e ON e.source_id=s.id LEFT JOIN raw_evidence r ON r.id=e.evidence_id GROUP BY s.id ORDER BY s.id)t),
 'hash_errors',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')),'response_hash_errors',(SELECT count(*) FROM official_response WHERE sha256<>encode(sha256(raw_content),'hex')),
 'fixture_count',(SELECT count(*) FROM financial_event WHERE original_title ILIKE '%隔离测试%' OR document_id LIKE '%test-only%' OR original_title ILIKE '%Fixture%' OR original_title LIKE '%isolated.invalid%'),
 'gdelt_not_pending',(SELECT count(*) FROM raw_evidence WHERE source_id='GDELT' AND verification_status<>'PENDING'),
 'new_official_samples',(SELECT count(*) FROM raw_evidence WHERE source_id IN('FED','ECB','NBS','SEC') AND ingestion_kind='NEW_EVENT'),
 'macro_series',(SELECT json_agg(t) FROM (SELECT id,units,frequency,currency,currency_denominator FROM macro_series ORDER BY id)t),
 'macro_counts',(SELECT json_agg(t) FROM (SELECT series_id,count(*) versions,count(DISTINCT observation_date) observations,min(observation_date) first_date,max(observation_date) last_date FROM macro_observation GROUP BY series_id ORDER BY series_id)t),
 'candidate_count',(SELECT count(*) FROM event_candidate),'article_groups',(SELECT count(DISTINCT cluster_key) FROM event_candidate),'grouped_articles',(SELECT count(*) FROM (SELECT cluster_key FROM event_candidate GROUP BY cluster_key HAVING count(*)>1)t),
 'financial_theme_candidates',(SELECT count(*) FROM event_candidate WHERE topics<>'[]'::jsonb),
 'entities',(SELECT json_agg(t) FROM (SELECT type,status,count(*) FROM financial_entity GROUP BY type,status ORDER BY type,status)t),
 'relations',(SELECT COALESCE(json_agg(t),'[]'::json) FROM (SELECT status,count(*) FROM entity_relation GROUP BY status)t),
 'verified_events',(SELECT count(*) FROM event_candidate WHERE verification='VERIFIED'),
 'verified_relationship_without_proof',(SELECT count(*) FROM entity_relation r LEFT JOIN raw_evidence e ON e.id=r.evidence_id WHERE r.status='VERIFIED' AND (e.id IS NULL OR e.raw_content_hash<>encode(sha256(e.raw_content),'hex'))),
 'jobs',(SELECT json_agg(t) FROM (SELECT source_id,status,http_status,new_count,fetched_count,duplicate_count,error_code,error_detail,started_at,finished_at FROM ingestion_job WHERE source_id IN('NBS','ECB_DATA') ORDER BY started_at)t),
 'failed_jobs',(SELECT count(*) FROM ingestion_job WHERE status='FAILED'),'quarantine',(SELECT count(*) FROM ingestion_quarantine),
 'nbs_sections',(SELECT json_agg(t) FROM (SELECT section,count(DISTINCT evidence_id) evidence FROM response_evidence WHERE section<>'' GROUP BY section)t),
 'nbs_cross_sections',(SELECT count(*) FROM (SELECT evidence_id FROM response_evidence WHERE section<>'' GROUP BY evidence_id HAVING count(DISTINCT section)>1)t),
 'new_samples',(SELECT COALESCE(json_agg(t),'[]'::json) FROM (SELECT r.id,r.source_id,r.published_at,r.first_seen_at,r.ingested_at,min(o.created_at) outbox_created_at,min(d.sent_at) server_sent_at,min(d.acknowledged_at) client_acknowledged_at FROM raw_evidence r JOIN stream_outbox o ON o.evidence_id=r.id LEFT JOIN stream_delivery d ON d.outbox_id=o.id WHERE r.ingestion_kind='NEW_EVENT' GROUP BY r.id ORDER BY r.ingested_at DESC LIMIT 5)t),
 'sse',(SELECT json_build_object('deliveries',count(*),'ack',count(*) FILTER(WHERE acknowledged_at IS NOT NULL),'server_failures',count(*) FILTER(WHERE failure_kind IN('SERVER_SEND_EXCEPTION','OUTBOX_DELIVERY_FAILURE')),'network_client',count(*) FILTER(WHERE failure_kind='NETWORK_OR_CLIENT_DISCONNECT')) FROM stream_delivery),
 'sample_evidence',(SELECT json_agg(t) FROM (SELECT r.id,r.source_id,e.id event_id,e.original_title,r.source_url,r.published_at,r.first_seen_at,r.collected_at,r.ingested_at,r.raw_content_hash,r.revision,r.ingestion_kind FROM raw_evidence r JOIN financial_event e ON e.evidence_id=r.id WHERE r.id IN(SELECT DISTINCT ON(source_id) id FROM raw_evidence WHERE source_id IN('NBS','ECB_DATA','FED','ECB','GDELT') ORDER BY source_id,collected_at DESC))t),
 'migrations',(SELECT json_agg(t) FROM (SELECT version,success FROM flyway_schema_history ORDER BY installed_rank)t));`);
const events=sql('SELECT json_agg(id) FROM financial_event;');const eventSet=new Set(events);const lostEvents=baseline.events.filter(id=>!eventSet.has(id));if(lostEvents.length)throw new Error('历史事件丢失');
state.preservation={baseline_evidence:baseline.evidence.length,missing_or_changed:missing.length,baseline_events:baseline.events.length,missing_events:lostEvents.length};
for(const source of ['sec','fred']){const r=await fetch('http://127.0.0.1:18080/api/'+source+'/status',{method:'POST',headers:{Authorization:'Bearer '+process.env.HUANKAN_ADMIN_TOKEN,'Content-Type':'application/json'},body:'{}'});if(!r.ok)throw new Error(source+' status HTTP '+r.status);state[source]=await r.json();}
writeFileSync(process.argv[2]||'docs/evidence/s22-research-state.json',JSON.stringify(state,null,2));console.log(JSON.stringify({time:state.checked_at,preservation:state.preservation,evidence:state.evidence_count,hash_errors:state.hash_errors,candidates:state.candidate_count,groups:state.article_groups,entities:state.entities,macro_counts:state.macro_counts,verified_events:state.verified_events}));
