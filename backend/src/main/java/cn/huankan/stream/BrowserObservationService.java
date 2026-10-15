package cn.huankan.stream;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service public class BrowserObservationService {
 private final JdbcTemplate db;public BrowserObservationService(JdbcTemplate db){this.db=db;}
 public record Receipt(UUID deliveryId,UUID connectionId,UUID eventId,Instant receivedAt,Instant renderedAt,Double receivedMonotonicMs,Double renderMonotonicMs,Double clockOffsetMs,Double clockUncertaintyMs){}
 @Transactional public Object acknowledge(List<Receipt> receipts){
  if(receipts==null||receipts.size()>100)throw new IllegalArgumentException("确认批次超限");
  for(var r:receipts){
   if(r.deliveryId()==null||r.connectionId()==null||r.eventId()==null||r.receivedAt()==null||r.receivedAt().isAfter(Instant.now().plusSeconds(300))||r.receivedMonotonicMs()==null||!Double.isFinite(r.receivedMonotonicMs())||r.receivedMonotonicMs()<0||(r.renderedAt()==null)!=(r.renderMonotonicMs()==null)||r.renderMonotonicMs()!=null&&(!Double.isFinite(r.renderMonotonicMs())||r.renderMonotonicMs()<r.receivedMonotonicMs())||r.clockOffsetMs()!=null&&!Double.isFinite(r.clockOffsetMs())||r.clockUncertaintyMs()!=null&&(!Double.isFinite(r.clockUncertaintyMs())||r.clockUncertaintyMs()<0))throw new IllegalArgumentException("浏览器观测时间无效");
   if(!Boolean.TRUE.equals(db.queryForObject("SELECT EXISTS(SELECT 1 FROM stream_delivery d JOIN stream_outbox o ON o.id=d.outbox_id WHERE d.id=? AND d.connection_id=? AND o.event_id=? AND d.outcome='SENT')",Boolean.class,r.deliveryId(),r.connectionId(),r.eventId())))throw new IllegalArgumentException("投递、连接与事件不匹配");
   db.update("INSERT INTO browser_delivery_observation(delivery_id,connection_id,event_id,received_at,rendered_at,received_monotonic_ms,render_monotonic_ms,clock_offset_ms,clock_uncertainty_ms) VALUES(?,?,?,?,?,?,?,?,?) ON CONFLICT(delivery_id) DO NOTHING",r.deliveryId(),r.connectionId(),r.eventId(),java.sql.Timestamp.from(r.receivedAt()),r.renderedAt()==null?null:java.sql.Timestamp.from(r.renderedAt()),r.receivedMonotonicMs(),r.renderMonotonicMs(),r.clockOffsetMs(),r.clockUncertaintyMs());
  }return Map.of("message","浏览器实际观测已记录；不表示用户阅读");
 }
 public Object clock(){return Map.of("serverTime",Instant.now());}
 public Object status(){var rows=db.queryForList("SELECT b.*,d.delivery_type,d.replay,d.sent_at,o.created_at outbox_created_at,r.source_id,r.published_at source_published_at,r.first_seen_at,r.ingested_at,b.render_monotonic_ms-b.received_monotonic_ms browser_receive_to_render_ms FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id JOIN stream_outbox o ON o.id=d.outbox_id JOIN raw_evidence r ON r.id=o.evidence_id ORDER BY b.acknowledged_at DESC LIMIT 100");Long samples=db.queryForObject("SELECT count(DISTINCT b.event_id) FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id JOIN stream_outbox o ON o.id=d.outbox_id JOIN raw_evidence r ON r.id=o.evidence_id JOIN source_registry s ON s.id=r.source_id WHERE d.delivery_type='LIVE' AND NOT d.replay AND o.notification_kind='NEW_EVENT' AND s.level='L0' AND b.rendered_at IS NOT NULL",Long.class);Long dual=db.queryForObject("SELECT count(*) FROM (SELECT b.event_id FROM browser_delivery_observation b JOIN stream_delivery d ON d.id=b.delivery_id JOIN stream_outbox o ON o.id=d.outbox_id JOIN raw_evidence r ON r.id=o.evidence_id JOIN source_registry s ON s.id=r.source_id WHERE d.delivery_type='LIVE' AND NOT d.replay AND o.notification_kind='NEW_EVENT' AND s.level='L0' AND b.rendered_at IS NOT NULL GROUP BY b.event_id HAVING count(DISTINCT b.connection_id)>=2)t",Long.class);return Map.of("samples",samples,"two_client_samples",dual,"state",dual==0?"在线实时传播尚未验证":"已有真实在线接收与渲染观测（不表示阅读）","observations",rows);}
}
