import cn.huankan.collector.CollectorGuard;
import cn.huankan.collector.SourceAdapter;
import cn.huankan.evidence.EvidenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** 在原JAR解出的实际类和依赖上验证；不重新编译或部署业务类。 */
public class OriginalRecoveryHarness {
 static HikariDataSource pool;static JdbcTemplate db;static TransactionTemplate tx;static final ObjectMapper json=new ObjectMapper();
 static final List<Map<String,Object>> tests=new ArrayList<>();static Path output;static String recoverySql;static boolean completed=false;
 interface Work{void run()throws Exception;}
 static void check(String name,Work w)throws Exception{long start=System.nanoTime();try{w.run();tests.add(Map.of("name",name,"passed",true,"ms",(System.nanoTime()-start)/1_000_000));}catch(Throwable e){tests.add(Map.of("name",name,"passed",false,"error",e.toString()));save();throw e;}save();}
 static void save()throws Exception{json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),Map.of("at",Instant.now().toString(),"tests",tests,"count",tests.size(),"completed",completed,"passed",completed&&tests.stream().allMatch(t->Boolean.TRUE.equals(t.get("passed"))),"scope","原JAR实际类、55434克隆测试schema；不访问5432，不采集外网，不生成新的金融事件"));}
 static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
 static long count(String table){return db.queryForObject("SELECT count(*) FROM "+table,Long.class);}
 static UUID job(String source){UUID id=UUID.randomUUID();db.update("INSERT INTO ingestion_job(id,source_id,status,started_at) VALUES(?,?,'RUNNING',now()-interval '1 day')",id,source);return id;}
 static UUID expiredLease(String source){UUID owner=UUID.randomUUID();db.update("INSERT INTO collector_lease(source_id,owner,acquired_at,heartbeat_at,expires_at) VALUES(?,?,now()-interval '1 day',now()-interval '1 day',now()-interval '23 hours') ON CONFLICT(source_id) DO UPDATE SET owner=excluded.owner,acquired_at=excluded.acquired_at,heartbeat_at=excluded.heartbeat_at,expires_at=excluded.expires_at",source,owner);return owner;}
 static String snapshot(UUID id){return db.queryForObject("SELECT to_jsonb(j)::text FROM ingestion_job j WHERE id=?",String.class,id);}
 static void recover(UUID id,String expected)throws Exception{
  try(Connection c=pool.getConnection()){c.setAutoCommit(false);try{
   try(var s=c.createStatement()){s.execute("SET LOCAL statement_timeout='3s'; SET LOCAL lock_timeout='500ms'");}
   try(var p=c.prepareStatement("SELECT set_config('huankan.inc02.job_id',?,true),set_config('huankan.inc02.expected_job',?,true)")){p.setString(1,id.toString());p.setString(2,expected);p.execute();}
   try(var s=c.createStatement()){s.execute(recoverySql);}c.commit();
  }catch(Exception e){c.rollback();throw e;}}
 }
 static void rejected(UUID id,String expected)throws Exception{String before=snapshot(id);long audits=count("audit_log");boolean refused=false;try{recover(id,expected);}catch(SQLException e){refused=true;}require(refused,"应拒绝恢复");require(before.equals(snapshot(id))&&audits==count("audit_log"),"拒绝后必须原子回滚");}
 public static void main(String[]args)throws Exception{
  output=Path.of(args[0]);recoverySql=Files.readString(Path.of(args[1]));String url=System.getenv("HUANKAN_INC02_JDBC_URL");
  if(url==null||!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:55434/huankan_r4_test\\?currentSchema=inc02_[a-f0-9]{32}"))throw new IllegalStateException("仅允许独立隔离目标");
  HikariConfig cfg=new HikariConfig();cfg.setJdbcUrl(url);cfg.setUsername(System.getenv("HUANKAN_TEST_DB_USER"));cfg.setPassword(System.getenv("HUANKAN_TEST_DB_PASSWORD"));cfg.setMaximumPoolSize(2);cfg.setMinimumIdle(1);cfg.setConnectionTimeout(300);cfg.setRegisterMbeans(true);cfg.setPoolName("Inc02Harness");
  pool=new HikariDataSource(cfg);db=new JdbcTemplate(pool);tx=new TransactionTemplate(new DataSourceTransactionManager(pool));
  if(args.length>2&&args[2].equals("fingerprint-only")){
   try{var r=db.queryForMap("SELECT r.*,e.original_title,e.category FROM raw_evidence r JOIN financial_event e ON e.evidence_id=r.id LIMIT 1");
    var i=new SourceAdapter.Item(r.get("source_document_id").toString(),r.get("original_title").toString(),r.get("source_url").toString(),r.get("published_at")==null?null:((Timestamp)r.get("published_at")).toInstant(),r.get("category").toString());
    json.writerWithDefaultPrettyPrinter().writeValue(output.resolveSibling("fixture-fingerprint.json").toFile(),Map.of("old",r.get("item_fingerprint"),"canonical",EvidenceService.fingerprint(i),"scope","只读原版指纹计算；仅新测试克隆导入准备"));
   }finally{pool.close();}return;
  }
  try{
   check("实际隔离库身份",()->require(db.queryForObject("SELECT inet_server_port()=55434 AND current_database()='huankan_r4_test' AND current_schema() LIKE 'inc02_%'",Boolean.class),"隔离身份不符"));
   check("原版51迁移成功",()->require(db.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success",Integer.class)==51,"迁移不符"));
   check("仅复制1条已有隔离证据和Outbox",()->require(count("raw_evidence")==1&&count("stream_outbox")==1,"隔离基线不符"));
   check("隔离许可准备且禁止真实采集",()->{db.update("UPDATE source_registry SET enabled=false");db.update("UPDATE source_license SET status='APPROVED',access_allowed=true,storage_allowed=true,display_allowed=true,reviewed_at=now(),review_basis='INC02隔离已有测试夹具，不采集外网',valid_until=now()+interval '1 day' WHERE source_id='FED'");require(db.queryForObject("SELECT count(*) FROM source_registry WHERE enabled",Integer.class)==0,"隔离来源意外启用");});
   UUID owner=expiredLease("ECB"),empty=job("ECB");String before=snapshot(empty);String cursor=db.queryForObject("SELECT coalesce((SELECT to_jsonb(c)::text FROM source_cursor c WHERE source_id='ECB'),'null')",String.class);
   check("过期ECB零提交任务审计恢复",()->{recover(empty,before);require("OWNER_LOST".equals(db.queryForObject("SELECT error_code FROM ingestion_job WHERE id=?",String.class,empty)),"终态未恢复");require(db.queryForObject("SELECT count(*) FROM audit_log WHERE action=?",Integer.class,"INC02_RECOVER_"+empty)==1,"缺审计");});
   String after=snapshot(empty);
   check("恢复重复执行幂等",()->{recover(empty,before);require(after.equals(snapshot(empty)),"重复执行更改终态");require(db.queryForObject("SELECT count(*) FROM audit_log WHERE action=?",Integer.class,"INC02_RECOVER_"+empty)==1,"重复审计");});
   check("不删除租约不推进ECB游标不增业务事件",()->{require(owner.equals(db.queryForObject("SELECT owner FROM collector_lease WHERE source_id='ECB'",UUID.class)),"租约改动");require(cursor.equals(db.queryForObject("SELECT coalesce((SELECT to_jsonb(c)::text FROM source_cursor c WHERE source_id='ECB'),'null')",String.class)),"游标改动");require(count("raw_evidence")==1&&count("financial_event")==1&&count("stream_outbox")==1,"业务记录改动");});
   check("审计保存完整前后状态",()->require(db.queryForObject("SELECT (target::jsonb)->'before'=?::jsonb AND (target::jsonb)->'after'=?::jsonb AND (target::jsonb)->>'classification'='NO_COMMITTED_EVIDENCE' FROM audit_log WHERE action=?",Boolean.class,before,after,"INC02_RECOVER_"+empty),"审计不完整"));
   UUID changed=job("ECB");String expected=snapshot(changed);db.update("UPDATE ingestion_job SET new_count=1 WHERE id=?",changed);
   check("现场计数变化拒绝并回滚",()->rejected(changed,expected));
   db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now() WHERE id=?",changed);
   UUID active=job("ECB");db.update("UPDATE collector_lease SET expires_at=now()+interval '1 hour' WHERE source_id='ECB'");
   check("未过期租约拒绝",()->rejected(active,snapshot(active)));
   db.update("UPDATE collector_lease SET expires_at=now()-interval '1 hour' WHERE source_id='ECB'");db.update("UPDATE ingestion_job SET heartbeat_at=now() WHERE id=?",active);
   check("新鲜心跳拒绝",()->rejected(active,snapshot(active)));db.update("UPDATE ingestion_job SET heartbeat_at=NULL WHERE id=?",active);
   check("仍有来源会话锁拒绝",()->{try(Connection hold=pool.getConnection();Statement s=hold.createStatement()){s.execute("SELECT pg_advisory_lock(742020,hashtext('ECB'))");try{rejected(active,snapshot(active));}finally{s.execute("SELECT pg_advisory_unlock(742020,hashtext('ECB'))");}}});
   db.update("UPDATE collector_lease SET owner=? WHERE source_id='ECB'",UUID.randomUUID());
   check("租约持有者变化拒绝",()->rejected(active,snapshot(active)));db.update("UPDATE collector_lease SET owner=? WHERE source_id='ECB'",owner);
   db.update("UPDATE ingestion_job SET status='FAILED',finished_at=now() WHERE id=?",active);
   UUID originalJob=db.queryForObject("SELECT job_id FROM raw_evidence LIMIT 1",UUID.class);UUID fedOwner=expiredLease("FED");
   db.update("UPDATE ingestion_job SET status='RUNNING',finished_at=NULL,lease_owner=?,new_count=0,heartbeat_at=NULL WHERE id=?",fedOwner,originalJob);
   String partial=snapshot(originalJob);
   check("有已提交证据的未结束任务保全",()->{recover(originalJob,partial);require(count("raw_evidence")==1&&count("stream_outbox")==1,"已提交数据改变");require(db.queryForObject("SELECT (target::jsonb)->>'classification'='COMMITTED_UNFINISHED_TASK' FROM audit_log WHERE action=?",Boolean.class,"INC02_RECOVER_"+originalJob),"提交分类不符");});
   check("原版锁机制恢复旧ECB任务",()->{UUID stale=job("ECB");var guard=new CollectorGuard(db);try(var lease=guard.acquire("ECB")){require(lease!=null,"未取得原版锁");require("OWNER_LOST".equals(db.queryForObject("SELECT error_code FROM ingestion_job WHERE id=?",String.class,stale)),"原版未标失败");}});
   check("原版活动会话锁避免重复采集",()->{var guard=new CollectorGuard(db);try(var lease=guard.acquire("ECB")){require(lease!=null,"未取得原版锁");require(guard.acquire("ECB")==null,"重复锁未跳过");}});
   var row=db.queryForMap("SELECT r.*,e.original_title,e.category FROM raw_evidence r JOIN financial_event e ON e.evidence_id=r.id LIMIT 1");
   var item=new SourceAdapter.Item(row.get("source_document_id").toString(),row.get("original_title").toString(),row.get("source_url").toString(),row.get("published_at")==null?null:((Timestamp)row.get("published_at")).toInstant(),row.get("category").toString());
   // R4夹具原指纹是占位符。只输出正确指纹，交给下一次克隆导入准备；不修改不可变证据。
   json.writerWithDefaultPrettyPrinter().writeValue(output.resolveSibling("fixture-fingerprint.json").toFile(),Map.of("old",row.get("item_fingerprint"),"canonical",EvidenceService.fingerprint(item),"scope","同一已有隔离记录的输入指纹；只能用于新克隆的导入准备"));
   check("已有隔离夹具指纹与原版输入一致",()->require(EvidenceService.fingerprint(item).equals(row.get("item_fingerprint")),"夹具输入指纹不一致；禁止修改不可变证据，须在新克隆导入前准备"));
   UUID retry;var guard=new CollectorGuard(db);
   try(var lease=guard.acquire("FED")){retry=job("FED");var service=new EvidenceService(db);
    check("原版重复保存不重复证据或Outbox",()->{int inserted=tx.execute(s->service.persist("FED",retry,(byte[])row.get("raw_content"),List.of(item),Instant.now(),true));require(inserted==0&&count("raw_evidence")==1&&count("stream_outbox")==1,"重复写入");});
    String cursorBefore=db.queryForObject("SELECT to_jsonb(c)::text FROM source_cursor c WHERE source_id='FED'",String.class);
    check("事务中断游标与数据原子回滚",()->{try{tx.execute(s->{service.persist("FED",retry,(byte[])row.get("raw_content"),List.of(item),Instant.now(),true);db.update("UPDATE source_cursor SET last_checked_at=now()+interval '1 day' WHERE source_id='FED'");throw new IllegalStateException("隔离事务中断");});}catch(IllegalStateException e){require(e.getMessage().equals("隔离事务中断"),"错误链不符");}require(cursorBefore.equals(db.queryForObject("SELECT to_jsonb(c)::text FROM source_cursor c WHERE source_id='FED'",String.class))&&count("raw_evidence")==1&&count("stream_outbox")==1,"回滚不完整");});
    db.update("UPDATE ingestion_job SET status='SUCCESS',finished_at=now() WHERE id=?",retry);
   }
   check("证据字节哈希与Outbox引用保持",()->require(db.queryForObject("SELECT count(*) FROM raw_evidence r JOIN stream_outbox o ON o.evidence_id=r.id JOIN financial_event e ON e.id=o.event_id WHERE r.raw_content_hash=encode(sha256(r.raw_content),'hex') AND e.evidence_id=r.id",Integer.class)==1,"证据或引用损坏"));
   check("隔离池饱和真实超时可观察",()->{try(Connection a=pool.getConnection();Connection b=pool.getConnection()){require(pool.getHikariPoolMXBean().getActiveConnections()==2,"活动连接测量错误");boolean timeout=false;try(Connection unused=pool.getConnection()){}catch(SQLTransientConnectionException e){timeout=true;System.out.println("INC02隔离告警：JDBC获取超时；"+e.getMessage());}require(timeout,"未产生预期连接超时");}});
   check("池连接释放后恢复",()->{try(Connection c=pool.getConnection()){require(c.isValid(1),"数据库未恢复");}});completed=true;
  }finally{pool.close();save();}
  System.out.println("本轮原版类隔离验证通过："+tests.size()+"项");
 }
}
