package cn.huankan.evidence;

import cn.huankan.collector.SourceAdapter.Item;
import java.security.MessageDigest;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceService {
    private final JdbcTemplate db;
    public EvidenceService(JdbcTemplate db) { this.db=db; }
    public static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    public static String fingerprint(Item item) {
        try { return hash(new ObjectMapper().writeValueAsBytes(java.util.Arrays.asList(item.documentId(),item.title(),item.url(),item.publishedAt()==null?null:item.publishedAt().toString(),item.category()))); }
        catch(Exception e) { throw new IllegalStateException(e); }
    }
    @Transactional public int persist(String source,UUID job,byte[] bytes,java.util.List<Item> items,Instant seen) {return persist(source,job,bytes,items,seen,false);}
    @Transactional public int persist(String source,UUID job,byte[] bytes,java.util.List<Item> items,Instant seen,boolean historicalImport) {
        // Ensure outbox sequence allocation and commit order agree across source transactions.
        db.execute("SELECT pg_advisory_xact_lock(742019)");
        var owner=db.queryForList("SELECT lease_owner FROM ingestion_job WHERE id=?",job);
        if(!owner.isEmpty()&&owner.getFirst().get("lease_owner")!=null){var lease=db.queryForList("SELECT owner FROM collector_lease WHERE source_id=? AND expires_at>now() FOR SHARE",source);if(lease.isEmpty()||!owner.getFirst().get("lease_owner").equals(lease.getFirst().get("owner")))throw new IllegalStateException("采集租约已失效，拒绝过期任务写入");}
        db.queryForObject("SELECT id FROM source_registry WHERE id=? FOR UPDATE",String.class,source);
        Boolean permitted=db.queryForObject("SELECT status='APPROVED' AND access_allowed AND storage_allowed AND display_allowed AND (valid_until IS NULL OR valid_until>now()) FROM source_license WHERE source_id=?",Boolean.class,source);
        if(!Boolean.TRUE.equals(permitted)) throw new IllegalStateException("来源许可不可用");
        var cursors=db.queryForList("SELECT monitoring_started_at FROM source_cursor WHERE source_id=?",source);
        Instant monitoringStart=cursors.isEmpty()||cursors.getFirst().get("monitoring_started_at")==null?null:((Timestamp)cursors.getFirst().get("monitoring_started_at")).toInstant();
        String sha=hash(bytes); int count=0;
        for(var item:items) {
            if(item.publishedAt()!=null&&item.publishedAt().isAfter(seen.plusSeconds(300)))throw new IllegalArgumentException("官方发布时间超出采集时刻允许偏差");
            String fingerprint=source.equals("NBS")?hash(bytes):fingerprint(item);
            var previous=db.queryForList("SELECT r.id,r.revision,r.first_seen_at,r.item_fingerprint,e.original_title,e.category FROM raw_evidence r JOIN financial_event e ON e.evidence_id=r.id WHERE r.source_id=? AND r.source_document_id=? ORDER BY r.revision DESC LIMIT 1",source,item.documentId());
            // A sliding HTTP query window is transport metadata, not an official observation revision.
            boolean sameObservation=source.equals("ECB_DATA")&&!previous.isEmpty()&&item.title().equals(previous.getFirst().get("original_title"))&&item.category().equals(previous.getFirst().get("category"));
            if(!previous.isEmpty() && (sameObservation||fingerprint.equals(previous.getFirst().get("item_fingerprint")))) continue;
            UUID evidence=UUID.randomUUID();
            int revision=previous.isEmpty()?1:((Number)previous.getFirst().get("revision")).intValue()+1;
            String kind=revision>1?"REVISION":historicalImport||monitoringStart==null||item.publishedAt()==null||!item.publishedAt().isAfter(monitoringStart)?"HISTORICAL_BACKFILL":"NEW_EVENT";
            Object firstSeen=previous.isEmpty()?Timestamp.from(seen):previous.getFirst().get("first_seen_at");
            db.update("INSERT INTO raw_evidence(id,source_id,source_document_id,source_url,published_at,missing_time_reason,first_seen_at,raw_content,raw_content_hash,item_fingerprint,content_language,parser_version,revision,verification_status,job_id,collected_at,ingestion_kind) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",evidence,source,item.documentId(),item.url(),item.publishedAt()==null?null:Timestamp.from(item.publishedAt()),item.publishedAt()==null?"原始响应未提供可信发布时间":null,firstSeen,bytes,sha,fingerprint,source.equals("GDELT")?"und":source.equals("NBS")?"zh-CN":"en","1.0",revision,item.publishedAt()==null||source.equals("GDELT")?"PENDING":"VERIFIED",job,Timestamp.from(seen),kind);
            db.update("INSERT INTO evidence_version VALUES(?,?,?)",evidence,previous.isEmpty()?null:previous.getFirst().get("id"),revision==1?"INITIAL":"METADATA_CHANGED");
            UUID event=db.queryForObject("INSERT INTO financial_event(id,source_id,document_id,original_title,category,evidence_id,ingestion_kind) VALUES(?,?,?,?,?,?,?) ON CONFLICT(source_id,document_id) DO UPDATE SET original_title=excluded.original_title,evidence_id=excluded.evidence_id,ingestion_kind=excluded.ingestion_kind,updated_at=now() RETURNING id",UUID.class,UUID.randomUUID(),source,item.documentId(),item.title(),item.category(),evidence,kind);
            db.update("INSERT INTO event_evidence VALUES(?,?)",event,evidence);
            // Aggregated metadata lacks an original publication timestamp. Its backfill/revisions
            // must never become a freshly published-news notification.
            db.update("INSERT INTO stream_outbox(event_id,evidence_id,notification_kind) VALUES(?,?,?)",event,evidence,source.equals("GDELT")||source.equals("FRED")||source.equals("ECB_DATA")?"HISTORICAL_BACKFILL":kind);
            count++;
        }
        db.update("INSERT INTO source_cursor(source_id,monitoring_started_at,last_checked_at) VALUES(?,?,?) ON CONFLICT(source_id) DO UPDATE SET monitoring_started_at=COALESCE(source_cursor.monitoring_started_at,excluded.monitoring_started_at)",source,Timestamp.from(seen),Timestamp.from(seen));
        return count;
    }
}
