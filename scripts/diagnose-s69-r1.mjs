import fs from 'node:fs';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {sql} from './s46-db.mjs';
const stamp=new Date().toISOString().replace(/[-:]/g,'').replace(/\..*/,''),prefix=`docs/evidence/s69-r1-diagnosis-${stamp}`;
const sensitive=/(password|pwd|token|secret|authorization)\s*[=:]\s*[^\s,;&]+/gi;
const sanitize=s=>s.replace(sensitive,'$1=[已脱敏]').replace(/(jdbc:postgresql:\/\/[^\s?]+)\?[^\s]+/g,'$1?[连接参数已脱敏]');
const manifest=[],blocks=[];
for(const name of ['s70-main','s70-research','s64-research','s65-research','s66-research']){
 const file=`runtime/${name}.log`;if(!fs.existsSync(file))continue;const bytes=fs.readFileSync(file),text=bytes.toString('utf8'),snapshot=`${prefix}-${name}.log`;
 fs.writeFileSync(snapshot,sanitize(text),{flag:'wx'});manifest.push({source:file,source_sha256:crypto.createHash('sha256').update(bytes).digest('hex'),snapshot,sha256:crypto.createHash('sha256').update(fs.readFileSync(snapshot)).digest('hex'),redacted:sanitize(text)!==text});
 for(const part of text.split(/(?=^\d{4}-\d\d-\d\dT)/m))if(/SQLException|CannotGetJdbcConnectionException/.test(part)){
  const appFrames=part.split(/\r?\n/).filter(x=>/cn\.huankan|Caused by|Exception|HikariPool.*timed out/.test(x));
  blocks.push({source:file,at:part.match(/^\S+/)?.[0],name:appFrames.find(x=>/\bat cn\.huankan/.test(x))?.trim(),exception_chain:sanitize(part),summary_frames:appFrames.map(sanitize),sqlstate:'原日志未记录，不能反推',error_code:'原日志未记录，不能反推',sql_execution:'这些调用在获取连接时失败，日志未确认实际执行SQL',transaction_state:'故障时未保留事务快照，未知'});
 }
}
const formal=sql("BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; SELECT json_build_object('at',clock_timestamp(),'database',current_database(),'user',current_user,'server_port',inet_server_port(),'server_version',current_setting('server_version'),'started_at',pg_postmaster_start_time(),'settings',(SELECT json_agg(t) FROM(SELECT name,setting,unit,source FROM pg_settings WHERE name IN('max_connections','superuser_reserved_connections','reserved_connections','statement_timeout','lock_timeout','idle_in_transaction_session_timeout','idle_session_timeout','log_min_duration_statement','log_lock_waits','deadlock_timeout','track_activities','shared_preload_libraries','tcp_keepalives_idle','tcp_keepalives_interval','tcp_keepalives_count','log_directory','logging_collector','log_destination'))t),'extensions',(SELECT json_agg(extname) FROM pg_extension),'activity',(SELECT json_agg(t) FROM(SELECT pid,client_addr,client_port,application_name,state,wait_event_type,wait_event,xact_start,query_start,state_change,pg_blocking_pids(pid) blockers,left(query,1000) query FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid())t),'locks',(SELECT json_agg(t) FROM(SELECT locktype,mode,granted,count(*) records FROM pg_locks WHERE database=(SELECT oid FROM pg_database WHERE datname=current_database()) GROUP BY locktype,mode,granted)t)); COMMIT;");
function testSql(query){if(process.env.HUANKAN_TEST_DB_URL!=='jdbc:postgresql://127.0.0.1:55432/huankan_test')throw Error('隔离测试实例身份配置不匹配');return JSON.parse(execFileSync('D:/Program Files/PostgreSQL/18/bin/psql.exe',['-h','127.0.0.1','-p','55432','-U',process.env.HUANKAN_TEST_DB_USER,'-d','huankan_test','-w','-Atq','-v','ON_ERROR_STOP=1','-f','-'],{env:{...process.env,PGPASSWORD:process.env.HUANKAN_TEST_DB_PASSWORD,PGCLIENTENCODING:'UTF8'},input:query,encoding:'utf8'}));}
const test=testSql("BEGIN READ ONLY; SELECT json_build_object('at',clock_timestamp(),'database',current_database(),'user',current_user,'server_port',inet_server_port(),'version',current_setting('server_version'),'started_at',pg_postmaster_start_time(),'address',inet_server_addr(),'max_connections',current_setting('max_connections')); COMMIT;");
if(test.database!=='huankan_test'||test.server_port!==55432||test.address!=='127.0.0.1')throw Error('隔离测试实例实际身份不匹配');
const record={at:new Date().toISOString(),scope:'只读正式库及实际日志诊断；未操作服务或制造正式故障',blocks,formal,test,manifest,limits:['历史15:55—15:56没有pg_stat_activity/锁/事务或逐SQL耗时快照；当前活动不能替代历史','已证实Hikari池10连接全占用及获取超时，持有连接的具体任务/SQL仍需独立证据','未开启pg_stat_statements或请求访问日志时，不能编造历史请求开始或SQLState']};
fs.writeFileSync(prefix+'.json',JSON.stringify(record,null,2),{flag:'wx'});
console.log(JSON.stringify({report:prefix+'.json',failure_blocks:blocks.map(x=>({source:x.source,at:x.at,name:x.name})),formal:{port:formal.server_port,extensions:formal.extensions,settings:formal.settings},test}));
