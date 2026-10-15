package cn.huankan.event;
import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.transaction.annotation.Transactional;
@Service public class EventLifecycleService {
 private final JdbcTemplate db;public EventLifecycleService(JdbcTemplate d){db=d;}
 private void visible(UUID id){if(id==null||!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM financial_event e JOIN source_license l ON l.source_id=e.source_id WHERE e.id=? AND l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()))",Boolean.class,id)))throw new IllegalArgumentException("事件不存在或不允许展示");}
 @Transactional public Object action(UUID id,String owner,String state,String reason){visible(id);if(state==null||!Set.of("RESEARCHING","ARCHIVED").contains(state)||reason==null||reason.strip().length()<10||reason.length()>1000)throw new IllegalArgumentException("研究动作或理由无效");db.update("INSERT INTO event_research_activity(id,event_id,owner_key,state,reason) VALUES(?,?,?,?,?)",UUID.randomUUID(),id,owner,state,reason.strip());return timeline(id,owner);}
 public Object timeline(UUID id,String owner){visible(id);var rows=db.queryForList("""
 SELECT at,state,reason,evidence_id FROM (
 SELECT r.first_seen_at at,'DISCOVERED' state,'实际首次发现，尚待核验' reason,r.id evidence_id FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id WHERE e.id=?
 UNION ALL SELECT r.collected_at,CASE WHEN r.revision>1 THEN 'NEW_EVIDENCE' ELSE 'PENDING' END,'实际保存证据版本 '||r.revision,r.id FROM event_evidence ee JOIN raw_evidence r ON r.id=ee.evidence_id WHERE ee.event_id=?
 UNION ALL SELECT f.created_at,f.verification_status,'事实版本 '||f.version,fe.evidence_id FROM fact_candidate f JOIN fact_evidence fe ON fe.fact_id=f.id WHERE f.event_id=?
 UNION ALL SELECT v.created_at,v.decision,v.reason,v.evidence_id FROM fact_review v JOIN fact_candidate f ON f.id=v.fact_id WHERE f.event_id=?
 UNION ALL SELECT a.created_at,'RESEARCHING','规则研究版本 '||a.rule_version,a.input_evidence_id FROM impact_assessment a JOIN fact_candidate f ON f.id=a.fact_id WHERE f.event_id=?
 UNION ALL SELECT created_at,state,reason,NULL::uuid FROM event_research_activity WHERE event_id=? AND owner_key=?
 )t ORDER BY at,state,evidence_id LIMIT 200
 """,id,id,id,id,id,id,owner);return Map.of("event_id",id,"timeline",rows,"boundary","研究和归档为个人工作状态；来源确认、字段核对与冲突只能来自实际事实记录，阅读不改变核验。最多200条审计记录。市场结果仍待验证。");}
}
