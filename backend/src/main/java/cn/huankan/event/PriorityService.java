package cn.huankan.event;

import cn.huankan.evidence.EvidenceService;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;

@Service public class PriorityService {
 private final JdbcTemplate db;private final ObjectMapper json;
 public PriorityService(JdbcTemplate d,ObjectMapper j){db=d;json=j;}
 public record Query(String country,String category,String verification,int minimum,boolean officialOnly,boolean watchOnly,Instant start,Instant end,String source){
  public Query(String country,String category,String verification,int minimum,boolean officialOnly,boolean watchOnly,Instant start,Instant end){this(country,category,verification,minimum,officialOnly,watchOnly,start,end,null);}
 }
 private String text(String v){return v==null?"":v;}
 private String encode(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalArgumentException("评分快照编码失败",e);}}
 private Instant instant(Object v){return v instanceof java.sql.Timestamp t?t.toInstant():null;}
 @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) public Object overview(String owner){
  var counts=db.queryForMap("""
   SELECT clock_timestamp() queried_at,
   count(*) FILTER(WHERE s.level='L0') official_events,
   count(*) FILTER(WHERE s.level='L0' AND r.published_at>=now()-interval '7 days' AND r.published_at<=now()) recent_official_events,
   count(*) FILTER(WHERE s.level='L2') pending_leads,
   count(*) FILTER(WHERE EXISTS(SELECT 1 FROM user_watch w WHERE w.owner_key=? AND w.kind='EVENT' AND w.value=e.id::text AND w.enabled)) followed_events,
   max(r.collected_at) data_updated_at
   FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id JOIN source_registry s ON s.id=e.source_id
   JOIN source_license l ON l.source_id=s.id WHERE l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now())
   """,owner);
  counts.put("sources",db.queryForList("SELECT s.id,s.name,s.level,s.enabled,j.status,j.http_status,j.error_code,j.finished_at FROM source_registry s LEFT JOIN LATERAL(SELECT status,http_status,error_code,finished_at FROM ingestion_job WHERE source_id=s.id ORDER BY started_at DESC,id DESC LIMIT 1)j ON true ORDER BY s.id"));
  return counts;
 }
 @Transactional(isolation=Isolation.REPEATABLE_READ) public Object rank(Query q,String owner){
  Instant asOf=Instant.now();if(q.minimum()<0||q.minimum()>100||q.start()!=null&&q.end()!=null&&!q.start().isBefore(q.end())||q.end()!=null&&q.end().isAfter(asOf))throw new IllegalArgumentException("排序范围无效");
  var watches=db.queryForList("SELECT kind,value,include_leads,minimum_priority FROM user_watch WHERE owner_key=? AND enabled ORDER BY kind,value",owner);
  // One source document group contributes one row; repeated headlines never increase weight.
  var rows=db.queryForList("""
    WITH candidates AS (
    SELECT e.id FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id LEFT JOIN macro_observation cm ON cm.evidence_id=r.id
    WHERE l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now())
    AND (NOT ? OR s.level='L0') AND (?='' OR s.region=?)
    AND (?='' OR s.id=?)
    AND (cm.series_id IS NULL OR cm.observation_date=(SELECT max(newer.observation_date) FROM macro_observation newer JOIN raw_evidence nr ON nr.id=newer.evidence_id WHERE newer.series_id=cm.series_id AND nr.collected_at<=now() AND newer.observation_date<=(now() AT TIME ZONE 'UTC')::date))
    AND (?::timestamptz IS NULL OR r.published_at>=?::timestamptz) AND (?::timestamptz IS NULL OR r.published_at<?::timestamptz)
    ORDER BY (s.level='L0') DESC,COALESCE(r.published_at,r.collected_at) DESC,e.id LIMIT 5001)
    SELECT DISTINCT ON(r.source_id,CASE WHEN r.source_id='NBS' THEN r.raw_content_hash||'|'||COALESCE(r.published_at::text,'unknown')||'|'||e.original_title ELSE r.source_document_id END) e.id,e.original_title,e.chinese_title,e.translation_status,e.category,(SELECT count(*) FROM candidates) candidate_count,
    r.id evidence_id,r.source_id,r.source_document_id,r.source_url,r.raw_content_hash,r.published_at,r.collected_at,r.revision,r.ingestion_kind,r.first_seen_at,
    r.raw_content_hash=encode(sha256(r.raw_content),'hex') intact,s.level,s.name publisher,s.region,
    m.series_id,m.observation_date,m.value,m.version,
    COALESCE((SELECT COALESCE((SELECT decision FROM fact_review v WHERE v.fact_id=f.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1),f.verification_status) FROM fact_candidate f WHERE f.event_id=e.id OR f.observation_id=r.id ORDER BY f.created_at DESC,f.id DESC LIMIT 1),'EXTRACTED') fact_status,
    watch_matches(?,r.id,e.id,s.id,s.level,COALESCE(g.priority,0)) watch_match,
    EXISTS(SELECT 1 FROM event_entity ee JOIN entity_relation er ON er.subject_id=ee.entity_id OR er.object_id=ee.entity_id WHERE ee.event_id=e.id AND ee.status='VERIFIED' AND er.status='VERIFIED') verified_relation
    FROM candidates c JOIN financial_event e ON e.id=c.id JOIN raw_evidence r ON r.id=e.evidence_id JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id
    LEFT JOIN macro_observation m ON m.evidence_id=r.id LEFT JOIN governed_message g ON g.event_id=e.id
    ORDER BY r.source_id,CASE WHEN r.source_id='NBS' THEN r.raw_content_hash||'|'||COALESCE(r.published_at::text,'unknown')||'|'||e.original_title ELSE r.source_document_id END,r.first_seen_at,e.id,r.revision DESC,r.collected_at DESC LIMIT 5001
    """,q.officialOnly(),text(q.country()),text(q.country()),text(q.source()),text(q.source()),q.start()==null?null:java.sql.Timestamp.from(q.start()),q.start()==null?null:java.sql.Timestamp.from(q.start()),q.end()==null?null:java.sql.Timestamp.from(q.end()),q.end()==null?null:java.sql.Timestamp.from(q.end()),owner);
  boolean truncated=!rows.isEmpty()&&((Number)rows.getFirst().get("candidate_count")).longValue()>5000;if(rows.size()>5000){rows.sort(Comparator.<Map<String,Object>,Boolean>comparing(r->!"L0".equals(r.get("level"))).thenComparing(r->Objects.toString(r.get("published_at"),Objects.toString(r.get("collected_at"),"")),Comparator.reverseOrder()).thenComparing(r->r.get("id").toString()));rows.removeLast();}var inputs=new ArrayList<Object>();var results=new ArrayList<Map<String,Object>>();
  for(var row:rows){
   var cats=PriorityRules.categories(row.get("original_title").toString(),(String)row.get("series_id"));
   if(!text(q.category()).isEmpty()&&!cats.contains(q.category()))continue;
   String status=row.get("fact_status").toString();if(!text(q.verification()).isEmpty()&&!status.equals(q.verification()))continue;
   var comparison=change(row);row.put("actual_change",comparison);boolean changed=comparison.get("difference") instanceof java.math.BigDecimal v&&v.signum()!=0;boolean watch=Boolean.TRUE.equals(row.get("watch_match"));if(q.watchOnly()&&!watch)continue;
   var input=new PriorityRules.Input(row.get("id").toString(),row.get("evidence_id").toString(),row.get("level").toString(),row.get("publisher").toString(),instant(row.get("published_at")),asOf,Boolean.TRUE.equals(row.get("intact")),status,cats,watch,changed,Boolean.TRUE.equals(row.get("verified_relation")),((Number)row.get("revision")).intValue()>1,row.get("ingestion_kind").toString());
   var score=PriorityRules.score(input);inputs.add(Map.of("fields",input,"evidence_hash",row.get("raw_content_hash"),"source_document_id",row.get("source_document_id"),"actual_change",comparison));
   row.put("score",score.total());row.put("components",score.components());row.put("reasons",score.reasons());row.put("categories",cats);row.put("rule_version",PriorityRules.VERSION);row.put("cluster_basis","NBS".equals(row.get("source_id"))?"官方单篇全文哈希、精确原始标题与发布时间均一致时合并多个URL；全部原始证据保留":"同来源同原始文档；保留最新证据版本，消息条数不加权；批量RSS响应哈希不作为单篇全文身份");row.put("actual_inputs",input);
   if(score.total()>=q.minimum())results.add(row);
  }
  results.sort(Comparator.<Map<String,Object>>comparingInt(r->(int)r.get("score")).reversed().thenComparing(r->instant(r.get("published_at")),Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r->r.get("observation_date") instanceof java.sql.Date d?d.toLocalDate():null,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(r->r.get("id").toString()));
  UUID id=UUID.randomUUID();String in=encode(Map.of("events",inputs,"watch_rules",watches,"rule_id",PriorityRules.RULE_ID,"rule_version",PriorityRules.VERSION,"calculated_at",asOf));
  var payload=new LinkedHashMap<String,Object>();payload.put("rule_id",PriorityRules.RULE_ID);payload.put("rows",results);payload.put("bounded",truncated);payload.put("candidate_limit",5000);payload.put("matched",results.size());payload.put("boundary","有界研究候选快照；每个宏观指标仅列最新真实观测，历史保留在宏观工作台。无一致口径变化或关系证据时对应维度为零。聚合线索不是已核实官方事件。");
  db.update("INSERT INTO research_priority_snapshot(id,owner_key,as_of,rule_version,filters,inputs,input_hash,results) VALUES(?,?,?,?,?::jsonb,?::jsonb,?,?::jsonb)",id,owner,java.sql.Timestamp.from(asOf),PriorityRules.VERSION,encode(q),in,db.queryForObject("SELECT encode(sha256(convert_to(?::jsonb::text,'UTF8')),'hex')",String.class,in),encode(payload));return snapshot(id,owner,0);
 }
 Map<String,Object> change(Map<String,Object> row){
  if(row.get("series_id")==null)return Map.of("comparison_status","没有实际宏观数值");
  var observations=db.queryForList("SELECT DISTINCT ON(m.observation_date) m.value,m.observation_date,m.evidence_id,r.raw_content_hash,f.fields FROM macro_observation m JOIN raw_evidence r ON r.id=m.evidence_id JOIN fact_candidate f ON f.observation_id=r.id WHERE m.series_id=? AND m.observation_date<=? AND r.collected_at<=? AND r.raw_content_hash=encode(sha256(r.raw_content),'hex') AND COALESCE((SELECT decision FROM fact_review v WHERE v.fact_id=f.id ORDER BY v.created_at DESC,v.id DESC LIMIT 1),f.verification_status) IN('FIELD_MATCHED','INDEPENDENT_SUPPORTED') ORDER BY m.observation_date DESC,m.version DESC,f.created_at DESC LIMIT 2",row.get("series_id"),row.get("observation_date"),row.get("collected_at"));
  if(observations.isEmpty()||!observations.getFirst().get("evidence_id").equals(row.get("evidence_id")))return Map.of("comparison_status","当前数值缺少字段核对证据");
  for(var r:observations)try{var f=json.readValue(r.remove("fields").toString(),Map.class);r.put("source_fields",f);r.put("unit",f.get("unit"));r.put("frequency",f.get("frequency"));}catch(Exception e){throw new IllegalArgumentException("宏观事实字段无效",e);}
  return MacroComparison.compare(observations);
 }
 public Object reproduce(UUID id,String owner){
  var row=db.queryForMap("SELECT rule_version,inputs,results,input_hash=encode(sha256(convert_to(inputs::text,'UTF8')),'hex') hash_valid FROM research_priority_snapshot WHERE id=? AND owner_key=?",id,owner);
  String version=row.get("rule_version").toString();
  try{var inputs=json.readTree(row.get("inputs").toString()).get("events");var saved=json.readTree(row.get("results").toString()).get("rows");var scores=new HashMap<String,PriorityRules.Score>();
   for(var input:inputs){var fields=json.treeToValue(input.get("fields"),PriorityRules.Input.class);scores.put(fields.eventId(),PriorityRules.score(fields,version));}
   int mismatches=0;for(var result:saved){var score=scores.get(result.path("id").asText());if(score==null||score.total()!=result.path("score").asInt()||!json.valueToTree(score.components()).equals(result.get("components")))mismatches++;}
   return Map.of("id",id,"rule_version",version,"hash_valid",row.get("hash_valid"),"input_count",inputs.size(),"result_count",saved.size(),"score_mismatches",mismatches,"reproduced",Boolean.TRUE.equals(row.get("hash_valid"))&&mismatches==0);
  }catch(Exception e){throw new IllegalArgumentException("冻结输入不能复算",e);}
 }
 public Object snapshot(UUID id,String owner,int page){if(page<0||page>100)throw new IllegalArgumentException("页码无效");var row=db.queryForMap("SELECT id,as_of,rule_version,input_hash,results FROM research_priority_snapshot WHERE id=? AND owner_key=?",id,owner);try{var all=json.readTree(row.remove("results").toString());var list=json.createArrayNode();var source=all.get("rows");for(int n=page*50;n<Math.min(source.size(),page*50+50);n++)list.add(source.get(n));row.put("rows",list);row.put("matched",all.get("matched"));row.put("bounded",all.get("bounded"));row.put("boundary",all.get("boundary"));row.put("page",page);row.put("rule_id",all.has("rule_id")?all.get("rule_id"):PriorityRules.RULE_ID);return row;}catch(Exception e){throw new IllegalArgumentException("评分快照无效",e);}}
}


