import fs from 'node:fs';
import crypto from 'node:crypto';
const input='docs/evidence/s69-r2-summary-20261014T013810.json';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const hash=p=>crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
const old=read(input), groups={};
const failures=old.formal_faults.failures.map((f,i)=>{
 const chain=f.exception_chain,thread=chain.match(/\[\s*([^\]]+)\]\s+\S+\s*:/)?.[1]??null;
 const key=f.scope;groups[key]=(groups[key]??0)+1;
 return {...f,number:i+1,category:'获取连接超时',exception_type:'java.sql.SQLTransientConnectionException',thread,
 request_source:chain.match(/Servlet\.service\(\).*?context with path \[([^\]]*)\]/)?.[1]??null,
 task:f.scheduled?f.scope:null,transaction_id:null,database_pid:null,sql_fingerprint:null,
 lock_wait:null,io_wait:null,recovered_at:null,
 evidence_gap:'原日志没有请求ID、连接ID、事务ID及PG进程关联。作用栈表示失败入口，不能作为长期占用者归因。首个后续健康成功不等于本请求恢复。'};
});
const frozen=read('docs/evidence/s69-r2-delivery-20261014T014238.json');
const rows=frozen.manifest;
const checks=rows.map(x=>({path:x.snapshot??x.file??x.path,expected:x.sha256}));
for(const c of checks){c.actual=hash(c.path);c.match=c.actual===c.expected;}
const out={at:new Date().toISOString(),source:{file:input,sha256:hash(input),raw_log:old.raw_log},count:failures.length,
 categories:{获取连接超时:failures.length,SQL执行失败:0,查询超时:0,事务回滚:0,连接断开:0},
 category_scope:'只对原36条失败的直接异常分类，锁与I/O是旁证机制，不重复计数为新增异常。',groups,
 scheduler_count:failures.filter(f=>f.scheduled).length,failures,prior_frozen_files:checks,
 confirmed_mechanism:'10个池连接均活动、无空闲导致30秒获取连接超时；正式PID43884存在证据提交全局锁、租约行锁与治理I/O长事务的同时竞争。',
 confirmed_paths:['GDELT整批事务嵌套EvidenceService.persist，持有742019至整批提交','ECB_DATA整序列事务嵌套EvidenceService.persist，同样延长742019及租约FOR SHARE','GovernanceService.process(500)查询和逐条治理同一事务持有742024','CollectorGuard每个来源合法持有专用连接贯穿HTTP，另有心跳获取池连接'],
 limitations:['不能将PG44696唯一归给GDELT或ECB_DATA','不能还原10个连接的全部连续持有者','DataFileRead存在，但磁盘、缓存或其他主机负载的占比未确定','R2线程转储与PG快照非同一时刻，不能伪造关联']};
const file=`docs/evidence/s69-r3-clusters-${Date.now()}.json`;
fs.writeFileSync(file,JSON.stringify(out,null,2),{flag:'wx'});
console.log(JSON.stringify({file,count:out.count,groups,scheduler:out.scheduler_count,frozen:checks.length,mismatch:checks.filter(c=>!c.match).length}));
