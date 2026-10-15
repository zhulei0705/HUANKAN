package cn.huankan.evidence;
import org.springframework.stereotype.Service;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;import org.springframework.scheduling.annotation.Scheduled;
@Service public class IntegrityMonitor {
 private final JdbcTemplate db;public IntegrityMonitor(JdbcTemplate db){this.db=db;}
 @Scheduled(initialDelay=10000,fixedDelay=2000) @Transactional public void scan(){
  if(!Boolean.TRUE.equals(db.queryForObject("SELECT pg_try_advisory_xact_lock(742026)",Boolean.class)))return;
  db.update("INSERT INTO evidence_integrity_check(evidence_id,checked_at,expected_hash,actual_hash,passed) SELECT r.id,clock_timestamp(),r.raw_content_hash,encode(sha256(r.raw_content),'hex'),r.raw_content_hash=encode(sha256(r.raw_content),'hex') FROM raw_evidence r LEFT JOIN evidence_integrity_check a ON a.evidence_id=r.id WHERE a.evidence_id IS NULL OR a.checked_at<now()-interval '24 hours' ORDER BY a.checked_at NULLS FIRST,r.id LIMIT 500 ON CONFLICT(evidence_id) DO UPDATE SET checked_at=excluded.checked_at,expected_hash=excluded.expected_hash,actual_hash=excluded.actual_hash,passed=excluded.passed");
 }
}
