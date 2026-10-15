package cn.huankan.event;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueryService {
    private final JdbcTemplate db;
    public QueryService(JdbcTemplate db) {this.db=db;}
    public Map<String,Object> events(String search,String source,int page) {
        return events(search,source,page,"","","official");
    }
    public Map<String,Object> events(String search,String source,int page,String region,String category,String sort) {
        if(search.length()>200 || source.length()>20 || page<0 || page>100000) throw new IllegalArgumentException("筛选参数无效");
        if(region.length()>100||category.length()>100)throw new IllegalArgumentException("筛选参数无效");
        String where=" WHERE e.source_id NOT IN ('ECB_DATA','FRED') AND (?='' OR e.original_title ILIKE '%' || ? || '%') AND ((?='' AND e.source_id<>'GDELT') OR e.source_id=?) AND (?='' OR s.region=? OR ?=ANY(string_to_array(d.countries,','))) AND (?='' OR e.category=?)";
        String order=switch(sort){case "published"->"r.published_at DESC NULLS LAST";case "firstSeen"->"r.first_seen_at DESC";case "official"->"s.level ASC,r.published_at DESC NULLS LAST";default->throw new IllegalArgumentException("排序参数无效");};
        String joins=" FROM financial_event e JOIN raw_evidence r ON r.id=e.evidence_id JOIN source_registry s ON s.id=e.source_id LEFT JOIN aggregated_dependency d ON d.evidence_id=r.id";
        String timing=" LEFT JOIN LATERAL(SELECT min(o.created_at) outbox_created_at,min(sd.sent_at) first_server_sent_at,min(sd.client_received_at) client_received_at,min(sd.acknowledged_at) client_acknowledged_at FROM stream_outbox o LEFT JOIN stream_delivery sd ON sd.outbox_id=o.id AND sd.outcome='SENT' AND NOT sd.replay AND sd.delivery_type IS DISTINCT FROM 'QUALIFICATION' WHERE o.evidence_id=r.id) delivery ON true";
        // Freeze the selected page before per-event delivery aggregation. Without this boundary,
        // PostgreSQL enriches every matching row before sorting and returning only fifty rows.
        String selected="WITH selected_page AS MATERIALIZED (SELECT e.id"+joins+where+" ORDER BY "+order+",e.id LIMIT 50 OFFSET ?) ";
        return Map.of("items",db.queryForList(selected+"SELECT e.*,r.source_url,r.published_at,r.first_seen_at,r.collected_at,r.ingested_at,r.verification_status,r.revision,s.name source_name,s.level,s.region,d.countries,d.themes,d.organizations,d.underlying_url,d.batch_processed_at,delivery.outbox_created_at,delivery.first_server_sent_at,delivery.client_received_at,delivery.client_acknowledged_at,CASE WHEN r.ingestion_kind='NEW_EVENT' AND delivery.client_acknowledged_at IS NOT NULL THEN extract(epoch FROM delivery.client_acknowledged_at-r.first_seen_at)*1000 END discovery_to_ack_ms,extract(epoch FROM r.ingested_at-r.first_seen_at)*1000 discovery_to_ingest_ms,CASE WHEN r.ingestion_kind='NEW_EVENT' THEN extract(epoch FROM r.first_seen_at-r.published_at)*1000 END publication_to_discovery_ms,extract(epoch FROM delivery.first_server_sent_at-r.ingested_at)*1000 ingest_to_first_send_ms"+joins+" JOIN selected_page selected ON selected.id=e.id"+timing+" ORDER BY "+order+",e.id",search,search,source,source,region,region,region,category,category,page*50),"total",db.queryForObject("SELECT count(*)"+joins+where,Long.class,search,search,source,source,region,region,region,category,category));
    }
    public Map<String,Object> stats() {
        return db.queryForMap("SELECT e.event_count,e.official_message_count,e.aggregated_lead_count,r.verified_count,r.pending_count,j.successful_jobs,j.failed_jobs,(SELECT count(*) FROM event_candidate WHERE verification='VERIFIED') fact_verified_count,(SELECT count(*) FROM macro_observation) macro_version_count,(SELECT count(*) FROM stream_outbox) outbox_count,(SELECT count(*) FROM ingestion_quarantine) quarantine_count,(SELECT count(*) FROM source_registry WHERE enabled) enabled_sources FROM (SELECT count(*) event_count,count(*) FILTER(WHERE s.level='L0' AND e.source_id NOT IN('ECB_DATA','FRED')) official_message_count,count(*) FILTER(WHERE e.source_id='GDELT') aggregated_lead_count FROM financial_event e JOIN source_registry s ON s.id=e.source_id)e CROSS JOIN (SELECT count(*) FILTER(WHERE verification_status='VERIFIED') verified_count,count(*) FILTER(WHERE verification_status='PENDING') pending_count FROM raw_evidence)r CROSS JOIN (SELECT count(*) FILTER(WHERE status='SUCCESS') successful_jobs,count(*) FILTER(WHERE status='FAILED') failed_jobs FROM ingestion_job)j");
    }
    public List<Map<String,Object>> sources() {
        return db.queryForList("""
            SELECT s.*,l.status license_status,l.terms_url,l.review_basis,l.usage_scope,l.valid_until,
            j.status job_status,j.started_at,j.finished_at,j.new_count,j.fetched_count,j.duplicate_count,j.duplicate_batch_count,j.quarantine_count,COALESCE(h.status,j.http_status) http_status,j.error_code,j.error_detail,COALESCE(h.duration_ms,j.duration_ms) duration_ms,h.etag,h.last_modified,
            m.last_check,m.last_success,m.success_rate,m.average_response_ms,m.error_count,
            anomaly.error_detail last_error_detail,anomaly.started_at last_error_at,
            (SELECT count(*) FROM ingestion_job WHERE source_id=s.id AND status='FAILED') failed_tasks,
            (SELECT count(*) FROM financial_event WHERE source_id=s.id) real_records,
            (SELECT max(first_seen_at) FROM raw_evidence WHERE source_id=s.id AND ingestion_kind='NEW_EVENT') last_new_event,
            (SELECT count(*) FROM ingestion_job WHERE source_id=s.id AND status='FAILED' AND started_at>COALESCE((SELECT max(started_at) FROM ingestion_job WHERE source_id=s.id AND status='SUCCESS'),'-infinity'::timestamptz)) consecutive_failures,
            s.enabled AND (m.last_check IS NULL OR m.last_check<now()-s.interval_seconds*3*interval '1 second') check_overdue,
            l.valid_until IS NOT NULL AND l.valid_until<=now() license_expired,
            (SELECT count(*) FROM stream_delivery WHERE outcome='FAILED' AND failure_kind IN('SERVER_SEND_EXCEPTION','OUTBOX_DELIVERY_FAILURE')) sse_failures,
            (SELECT count(*) FROM stream_delivery WHERE outcome='SENT' AND acknowledged_at IS NULL) sse_unacknowledged,
            (SELECT count(*) FROM stream_delivery WHERE outcome='SENT' AND replay) sse_replayed,
            (SELECT count(*) FROM stream_delivery WHERE outcome='FAILED' AND failure_kind IS NULL) sse_unclassified_failures,
            (SELECT count(*) FROM gdelt_gap WHERE state='MISSING_HISTORICAL_BATCH') gdelt_missing_batches,
            (SELECT availability FROM gdelt_batch ORDER BY batch_id DESC LIMIT 1) gdelt_availability,
            (SELECT batch_id FROM gdelt_batch ORDER BY batch_id DESC LIMIT 1) gdelt_latest_batch,
            (SELECT last_successful_batch FROM source_cursor WHERE source_id='GDELT') gdelt_completed_cursor,
            latency.samples new_latency_samples,CASE WHEN latency.samples>=20 THEN latency.p50 END discovery_p50_ms,CASE WHEN latency.samples>=20 THEN latency.p95 END discovery_p95_ms
            FROM source_registry s JOIN source_license l ON l.source_id=s.id
            LEFT JOIN LATERAL(SELECT * FROM ingestion_job WHERE source_id=s.id ORDER BY started_at DESC LIMIT 1) j ON true
            LEFT JOIN LATERAL(SELECT * FROM http_request_observation WHERE source_id=s.id ORDER BY attempted_at DESC LIMIT 1) h ON true
            LEFT JOIN LATERAL(SELECT error_detail,started_at FROM ingestion_job WHERE source_id=s.id AND status='FAILED' ORDER BY started_at DESC LIMIT 1) anomaly ON true
            LEFT JOIN LATERAL(SELECT max(attempted_at) last_check,max(completed_at) FILTER(WHERE status IN(200,206,304) AND error IS NULL) last_success,
             avg(CASE WHEN status IN(200,206,304) AND error IS NULL THEN 1.0 ELSE 0.0 END) success_rate,avg(duration_ms) average_response_ms,count(*) FILTER(WHERE error IS NOT NULL OR status IS NULL OR status NOT IN(200,206,304)) error_count FROM http_request_observation WHERE source_id=s.id) m ON true
            LEFT JOIN LATERAL(SELECT count(*) samples,percentile_cont(0.5) WITHIN GROUP(ORDER BY extract(epoch FROM first_seen_at-published_at)*1000) p50,percentile_cont(0.95) WITHIN GROUP(ORDER BY extract(epoch FROM first_seen_at-published_at)*1000) p95 FROM raw_evidence WHERE source_id=s.id AND ingestion_kind='NEW_EVENT' AND published_at IS NOT NULL AND published_at<=first_seen_at) latency ON true
            ORDER BY s.id
            """);
    }
    @org.springframework.transaction.annotation.Transactional
    public List<Map<String,Object>> alerts() {
        db.execute("SELECT pg_advisory_xact_lock(742021)");
        var alerts=new java.util.ArrayList<Map<String,Object>>();
        for(var s:sources()){
            String id=s.get("id").toString();
            if(Boolean.TRUE.equals(s.get("license_expired")))alerts.add(Map.of("source",id,"message","数据源许可已失效"));
            if(Boolean.TRUE.equals(s.get("check_overdue")))alerts.add(Map.of("source",id,"message","数据源未按预期间隔执行检查"));
            if(((Number)s.get("consecutive_failures")).longValue()>=3)alerts.add(Map.of("source",id,"message","数据源连续采集失败"));
            if(Integer.valueOf(429).equals(s.get("http_status")))alerts.add(Map.of("source",id,"message","官方接口返回429，正在限流冷却"));
            if(Boolean.TRUE.equals(s.get("enabled"))&&s.get("last_success") instanceof java.sql.Timestamp success&&success.toInstant().isBefore(java.time.Instant.now().minusSeconds(((Number)s.get("interval_seconds")).longValue()*6)))alerts.add(Map.of("source",id,"message","来源长时间未成功检查"));
        }
        long broken=db.queryForObject("SELECT count(*) FROM evidence_integrity_check WHERE NOT passed",Long.class);
        long unchecked=db.queryForObject("SELECT count(*) FROM raw_evidence r LEFT JOIN evidence_integrity_check a ON a.evidence_id=r.id WHERE a.evidence_id IS NULL OR a.checked_at<now()-interval '24 hours'",Long.class);
        if(unchecked>0)alerts.add(Map.of("source","系统","message","原始证据完整性分批校验尚未覆盖全部数据","count",unchecked));
        if(broken>0)alerts.add(Map.of("source","系统","message","原始证据哈希校验失败","count",broken));
        long failed=db.queryForObject("SELECT count(*) FROM stream_delivery WHERE outcome='FAILED' AND failure_kind IN('SERVER_SEND_EXCEPTION','OUTBOX_DELIVERY_FAILURE') AND sent_at>now()-interval '15 minutes'",Long.class);
        if(failed>0)alerts.add(Map.of("source","系统","message","最近15分钟存在SSE发送失败","count",failed));
        // Historical replay is demand-driven. Only new events awaiting an active subscriber indicate push backlog.
        long backlog=db.queryForObject("SELECT count(*) FROM stream_outbox o WHERE notification_kind='NEW_EVENT' AND created_at<now()-interval '5 minutes' AND EXISTS(SELECT 1 FROM stream_delivery WHERE outcome='SENT' AND sent_at>now()-interval '5 minutes') AND NOT EXISTS(SELECT 1 FROM stream_delivery d WHERE d.outbox_id=o.id AND outcome='SENT')",Long.class);
        if(backlog>0)alerts.add(Map.of("source","系统","message","有活跃推送记录时，新增事件等待发送超过5分钟","count",backlog));
        long gaps=db.queryForObject("SELECT count(*) FROM gdelt_gap WHERE state='MISSING_HISTORICAL_BATCH'",Long.class);
        if(gaps>0)alerts.add(Map.of("source","GDELT","message","官方历史索引中仍有待确认缺口","count",gaps));
        long unavailable=db.queryForObject("SELECT count(*) FROM gdelt_batch WHERE availability='PERMANENT_FAILURE' OR (availability IN('PUBLISHED_NOT_AVAILABLE','MISSING_HISTORICAL_BATCH') AND attempts>=3 AND retry_at IS NOT NULL)",Long.class);
        if(unavailable>0)alerts.add(Map.of("source","GDELT","message","已公布批次持续不可用或需人工处理","count",unavailable));
        long stale=db.queryForObject("SELECT count(*) FROM collector_lease WHERE expires_at<now()",Long.class);
        if(stale>0)alerts.add(Map.of("source","系统","message","采集租约心跳已超时","count",stale));
        var keys=new java.util.HashSet<String>();
        for(var alert:alerts){String key=alert.get("source")+":"+alert.get("message");keys.add(key);db.update("INSERT INTO monitor_alert(key,source,message,state) VALUES(?,?,?,'ACTIVE') ON CONFLICT(key) DO UPDATE SET state='ACTIVE',last_seen_at=now(),opened_at=CASE WHEN monitor_alert.state='RECOVERED' THEN now() ELSE monitor_alert.opened_at END,occurrences=monitor_alert.occurrences+CASE WHEN monitor_alert.state='RECOVERED' THEN 1 ELSE 0 END,recovered_at=NULL",key,alert.get("source"),alert.get("message"));}
        for(var row:db.queryForList("SELECT key FROM monitor_alert WHERE state='ACTIVE'"))if(!keys.contains(row.get("key")))db.update("UPDATE monitor_alert SET state='RECOVERED',recovered_at=now() WHERE key=?",row.get("key"));
        return alerts;
    }
    public List<Map<String,Object>> evidence(UUID event) {
        return db.queryForList("SELECT r.id,r.source_id,r.source_document_id,r.source_url,p.original_source_url,p.canonical_source_url,p.redirect_final_url,p.canonical_equivalence_confirmed,p.source_identity,p.integrity,p.fact_verification,p.independent_verification,r.published_at,r.missing_time_reason,r.first_seen_at,r.collected_at,r.ingested_at,r.ingestion_kind,r.raw_content_hash,r.content_language,r.parser_version,r.revision,r.verification_status,r.job_id,octet_length(r.raw_content) raw_bytes,l.status license_status,l.terms_url,d.batch_id,d.underlying_url,d.themes,d.organizations,d.countries,d.batch_processed_at FROM event_evidence ee JOIN raw_evidence r ON r.id=ee.evidence_id JOIN evidence_provenance p ON p.evidence_id=r.id JOIN source_license l ON l.source_id=r.source_id LEFT JOIN aggregated_dependency d ON d.evidence_id=r.id WHERE ee.event_id=? ORDER BY r.revision DESC",event);
    }
    public Map<String,Object> raw(UUID id) {
        var rows=db.queryForList("SELECT raw_content,raw_content_hash FROM raw_evidence WHERE id=?",id);
        if(rows.isEmpty()) throw new IllegalArgumentException("证据不存在"); return rows.getFirst();
    }
}
