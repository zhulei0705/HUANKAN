package cn.huankan.event;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Service;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.beans.factory.annotation.Value;
@Service public class WatchUpdateService {
 private final JdbcTemplate db;private final boolean enabled;
 public WatchUpdateService(JdbcTemplate d,@Value("${huankan.collector-enabled}")boolean e){db=d;enabled=e;}
 @Scheduled(fixedDelay=30000,initialDelay=45000)public void scheduled(){if(enabled)try{collect();}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).error("关注更新检查失败，保留持久化记录",e);}}
 public int collect(){return db.update("""
 INSERT INTO watch_update(owner_key,event_id,evidence_id,kind,reference_id,reason)
 SELECT DISTINCT w.owner_key,e.id,r.id,'OFFICIAL_UPDATE',r.id::text,'真实官方证据更新；命中持久化关注规则，来源等级L0，非历史回填；证据 '||r.id
 FROM user_watch w JOIN financial_event e ON true JOIN raw_evidence r ON r.id=e.evidence_id
 JOIN source_registry s ON s.id=r.source_id JOIN source_license l ON l.source_id=s.id JOIN notification_qualification q ON q.event_id=e.id AND q.evidence_id=r.id
 WHERE w.enabled AND r.collected_at>w.created_at AND r.ingestion_kind<>'HISTORICAL_BACKFILL' AND s.level='L0'
 AND l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) AND q.official_eligible
 AND r.raw_content_hash=encode(sha256(r.raw_content),'hex') AND watch_matches(w.owner_key,r.id,e.id,s.id,s.level,q.priority)
 AND NOT EXISTS(SELECT 1 FROM watch_update u WHERE u.owner_key=w.owner_key AND u.kind='OFFICIAL_UPDATE' AND u.reference_id=r.id::text)
 LIMIT 500 ON CONFLICT DO NOTHING
 """);}
 public Object query(String owner,long before){if(before<0)throw new IllegalArgumentException("更新游标无效");return db.queryForList("SELECT u.id,u.event_id,u.evidence_id,u.kind,u.reason,u.created_at,rr.read_at FROM watch_update u JOIN financial_event e ON e.id=u.event_id JOIN source_license l ON l.source_id=e.source_id LEFT JOIN watch_update_read rr ON rr.owner_key=u.owner_key AND rr.update_id=u.id WHERE u.owner_key=? AND (?=0 OR u.id<?) AND l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) ORDER BY u.id DESC LIMIT 50",owner,before,before);}
 public void read(String owner,long id){db.update("INSERT INTO watch_update_read(owner_key,update_id) SELECT ?,id FROM watch_update WHERE id=? AND owner_key=? ON CONFLICT DO NOTHING",owner,id,owner);}
}
