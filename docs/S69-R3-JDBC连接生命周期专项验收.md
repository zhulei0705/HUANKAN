# 寰瞰 HUANKAN · S69-R3 JDBC连接生命周期专项验收

**长事务和串行锁放大连接竞争的机制及对应候选整改已在隔离环境验证；正式历史底层根因尚未全部闭环，不能宣称根治或正式修复已完成。** 已确认正式PID43884的治理长事务与证据全局锁/租约行锁竞争；但PG44696不能唯一归为GDELT或ECB_DATA，36条失败缺少逐请求→连接→事务→PG进程的连续关联，慢DataFileRead成因及全部10个连接持有者仍未确定。

原健康窗口finished_at为2026-10-13 19:32:59.160，双浏览器窗口为19:48:01.017（北京时间）。原A/B/C失败，D等待真实官方双端LIVE样本0；结论不改变。原索引SHA-256为3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14。R2的806份冻结副本本轮核对差异0，见[s69-r3-clusters-1791944629934.json](evidence/s69-r3-clusters-1791944629934.json)。

## 36条失败分类与实际时间线

全部36条直接异常均为java.sql.SQLTransientConnectionException获取连接超时，其中调度7、其余29。SQL执行失败、查询超时、回滚及断开在这36条的直接分类中无独立记录，不代表其他历史时段不存在。资源等待、调度竞争为旁证机制，不重复累计为新增失败。SQLState及实际错误码原日志未输出，全部保持未知；请求客户端、事务标识、连接ID、PG进程、SQL指纹、逐次锁/I/O和逐次恢复时刻也保持未知。完整脱敏异常链逐条保存在[s69-r3-clusters-1791944629934.json](evidence/s69-r3-clusters-1791944629934.json)，来源原日志及SHA见该JSON，未伪造关联。日志线程标签可能被框架截断，不能仅凭线程名归责。

|异常栈失败入口|数量|
|---|---|
|event.QueryService.sources|9|
|event.QueryService.stats|7|
|event.QueryService.events|8|
|evidence.IntegrityMonitor$$SpringCGLIB$$0.scan|1|
|stream.StreamService.cursor|5|
|stream.QualificationAuditService$$SpringCGLIB$$0.auditNew|1|
|event.QueryService$$SpringCGLIB$$0.alerts|2|
|event.WatchUpdateService.collect|1|
|stream.StreamService.lambda$publish$5|2|

|编号|实际时间（含时区）|失败入口|原日志线程标签|获取等待ms|pending|
|---|---|---|---|---|---|
|1|2026-10-14T08:46:28.938+08:00|event.QueryService.sources|1-18080-exec-15|30008|8|
|2|2026-10-14T08:46:28.938+08:00|event.QueryService.stats|1-18080-exec-19|30008|8|
|3|2026-10-14T08:46:28.938+08:00|event.QueryService.events|1-18080-exec-23|30009|8|
|4|2026-10-14T08:46:29.769+08:00|evidence.IntegrityMonitor$$SpringCGLIB$$0.scan|scheduling-3|30011|7|
|5|2026-10-14T08:46:29.843+08:00|stream.StreamService.cursor|scheduling-4|30013|6|
|6|2026-10-14T08:46:51.206+08:00|event.QueryService.sources|.1-18080-exec-8|30006|6|
|7|2026-10-14T08:46:51.206+08:00|event.QueryService.events|1-18080-exec-20|30006|6|
|8|2026-10-14T08:46:51.206+08:00|event.QueryService.events|1-18080-exec-10|30006|5|
|9|2026-10-14T08:46:59.891+08:00|stream.StreamService.cursor|1-18080-exec-25|30013|9|
|10|2026-10-14T08:47:00.724+08:00|stream.QualificationAuditService$$SpringCGLIB$$0.auditNew|scheduling-3|30000|8|
|11|2026-10-14T08:47:01.030+08:00|event.QueryService$$SpringCGLIB$$0.alerts|scheduling-4|30001|8|
|12|2026-10-14T08:47:10.166+08:00|stream.StreamService.cursor|1-18080-exec-15|30002|8|
|13|2026-10-14T08:47:12.843+08:00|event.QueryService.stats|1-18080-exec-19|30000|7|
|14|2026-10-14T08:47:21.797+08:00|event.QueryService.sources|1-18080-exec-23|30001|8|
|15|2026-10-14T08:47:21.812+08:00|event.QueryService.stats|.1-18080-exec-6|30004|7|
|16|2026-10-14T08:47:28.888+08:00|event.QueryService.events|1-18080-exec-12|30001|8|
|17|2026-10-14T08:47:28.902+08:00|event.QueryService.stats|.1-18080-exec-8|30010|6|
|18|2026-10-14T08:47:28.902+08:00|event.QueryService.sources|.1-18080-exec-3|30011|6|
|19|2026-10-14T08:47:30.915+08:00|event.WatchUpdateService.collect|scheduling-3|30004|5|
|20|2026-10-14T08:47:31.086+08:00|stream.StreamService.cursor|scheduling-4|30015|5|
|21|2026-10-14T08:47:51.198+08:00|event.QueryService.events|1-18080-exec-10|30013|7|
|22|2026-10-14T08:47:51.198+08:00|event.QueryService.events|1-18080-exec-20|30014|6|
|23|2026-10-14T08:47:52.427+08:00|event.QueryService.sources|1-18080-exec-25|30009|6|
|24|2026-10-14T08:47:52.427+08:00|event.QueryService.sources|1-18080-exec-15|30009|7|
|25|2026-10-14T08:48:30.160+08:00|event.QueryService$$SpringCGLIB$$0.alerts|scheduling-4|30010|8|
|26|2026-10-14T08:48:30.206+08:00|event.QueryService.sources|1-18080-exec-25|30011|7|
|27|2026-10-14T08:48:30.207+08:00|event.QueryService.stats|1-18080-exec-15|30008|8|
|28|2026-10-14T08:48:50.422+08:00|event.QueryService.events|1-18080-exec-19|30002|8|
|29|2026-10-14T08:48:50.422+08:00|event.QueryService.stats|.1-18080-exec-7|30001|9|
|30|2026-10-14T08:48:57.441+08:00|stream.StreamService.lambda$publish$5|.1-18080-exec-6|30012|9|
|31|2026-10-14T08:48:57.453+08:00|stream.StreamService.lambda$publish$5|.1-18080-exec-6|30012|9|
|32|2026-10-14T08:50:09.564+08:00|event.QueryService.sources|1-18080-exec-19|30001|10|
|33|2026-10-14T08:50:09.840+08:00|event.QueryService.events|1-18080-exec-10|30014|7|
|34|2026-10-14T08:50:09.840+08:00|event.QueryService.stats|1-18080-exec-20|30013|7|
|35|2026-10-14T08:50:09.840+08:00|event.QueryService.sources|1-18080-exec-22|30013|7|
|36|2026-10-14T08:50:09.965+08:00|stream.StreamService.cursor|1-18080-exec-24|30004|7|

## 真实锁、I/O与恢复旁证

R2的08:50:53快照中：PG44696持有742019，事务始于08:45:25.033512，客户端56526；31256等待同锁，客户端61696；45728等待collector_lease行更新/事务1243063，客户端59279。PG57784事务始于08:41:29.267769，持有治理742024，客户端59288，出现DataFileRead；08:53:53仍为同一事务并执行research_case写入。对应客户端socket均归Windows43884，但缺job UUID→PG PID映射，不把持锁44696唯一归为GDELT。证据为[s69-r2-fault-locks-1791939053908.json](evidence/s69-r2-fault-locks-1791939053908.json)、[s69-r2-fault-sockets-20261014T084953.json](evidence/s69-r2-fault-sockets-20261014T084953.json)、[s69-r2-fault-20261014T005352.json](evidence/s69-r2-fault-20261014T005352.json)。8:56线程转储显示ECB_DATA.persist及ResearchService，但非同一时刻，不补历史关联。

R2任务表显示GDELT08:45:17.974295开始、08:52:42.981274成功结束，445007ms、465新记录；心跳最后值仍08:45:17.974295。FED后续08:50:39.386347完成，ECB08:52:47.788002完成，ECB_DATA08:56:53.140618完成。任务成功不证明期间请求未失败。首次后续健康成功的采集结束为08:54:46.932；36个失败请求的实际重试/恢复和治理事务最终提交时刻未知。见[s69-r2-business-1791941260402.json](evidence/s69-r2-business-1791941260402.json)及R2健康恢复证据。

## 生命周期审计与最小整改

GDELT在一整个解析批次外层事务逐条调用EvidenceService.persist；ECB_DATA在一整个SDMX序列事务内循环调用。内层@Transactional加入外层事务，742019全局证据/Outbox顺序锁、source_registry FOR UPDATE和租约FOR SHARE因此跨越整批，心跳更新同租约行会等待。网络下载和解析实际位于这些事务之外；但CollectorGuard专用会话锁连接跨越HTTP，是现有围栏设计，不能当作已证明连接泄漏。Governance/Research调度每次500条同事务查询及逐条处理会延长742024/742022和连接持有。

本轮GDELT每50个合格线索提交：证据、Outbox、聚合关联、断点、retained_count及任务计数同事务；失败仅回滚当前分批，已提交计数保留，最终完成批次前再次检查并锁住租约。只有完整批次才推进source_cursor/缺口状态并追加原不可变collector_batch_commit，失败部分仍由原始证据归属、任务计数、断点及完整导出证明；不修改旧收据，不把部分完成写成完整批次。ECB每50观测提交并保留同一官方response、macro元数据、观测和response_evidence原子关系；已提交new/fetched/duplicate计数每分批落库。两个采集器调用既有JdbcDiagnostics明确记录TASK_FAILED，不重做R2诊断能力。治理调度最多10个50条事务，保留每轮500条预算；残留记录后续可处理，异常保留日志，不静默丢任务。

EvidenceService证据和Outbox原子提交、742019提交顺序、来源许可与围栏未取消。JdbcTemplate执行后释放连接，ResultSet/Statement由模板或try-with-resources关闭；没有发现本轮目标路径的异常泄漏实证。未使用JPA/Hibernate，未发现ORM延迟加载路径。StreamService查询结果先物化并释放JDBC，随后emitter.send；SSE HTTP长连接不等于数据库连接长占用。Outbox消费与SSE既有竞态修复未重做。用户关注/查询没有发现跨网络的数据库事务。

生产连接池配置未修改，调度线程池仍4。正式日志直接证明max/total=10、active=10、idle=0；正式minIdle、validation/idle/maxLifetime/keepalive的有效运行值未读取到，不能以候选MXBean代替。R2同版本默认值max10/min10、获取30000ms、校验5000ms、idle600000ms、lifetime1800000ms、keepalive120000ms仅作版本基线。正常隔离比较使用10连接、min10、获取30000ms；分批失败测试使用独立4连接正常池，故障注入池仍max2/min1、获取300ms/校验250ms。新预热只等待原定两个槽位建好，未扩大故障容量或修改其超时。正式共享PG上限100、保留3，R3基线观察50个客户端，并非全属正式Java；隔离55432不占5432连接预算，但共享主机I/O。

## 隔离对照与验收结果

55432/huankan_test身份由测试准备查询实际核验，每个测试独立随机schema，不在正式库注入。直接加载冻结R2 JAR中的实际GdeltCollector类（SHA b624c1ec4c6ba658f56ab847881a811c3e42acdaca4e307a8343e43535345436，不是部署旧JAR）和本轮实际采集器，使用同120条压缩数据、第56条同一失败注入：旧版第51条时租约更新SQLState=55P03，整批失败后断点0、证据/Outbox均0；新版第51条可在200ms锁等待预算内更新心跳，失败保留断点50、已提交50，重试新增70，证据/Outbox最终120/120，失败任务仍1。ECB相同部分失败：50条保留，重试new=370、fetched=420、duplicates=50，最终观测/Outbox=420/420，失败及隔离记录1保留，来源证明错误0。

最终完整后端218项，失败0、错误0、跳过0；前端19项通过，独立目录类型检查和普通生产构建成功，静态配置另行生产重建成功。浏览器18项：通过18、意外0、跳过0、不稳定0。开发隔离10项，passed=true。120秒探针25轮，错误0、passed=true。这些是本轮实际重跑，不引用R2计数充当新增测试，不累加重试次数。

[s69-r3-artifacts-1791947383753.json](evidence/s69-r3-artifacts-1791947383753.json)包含每项XML、场景时间、池/事务/查询/吞吐样本；完整日志[s69-r3-backend-verified.log](../runtime/s69-r3-backend-verified.log)。[s69-r3-tested-jar-classes.json](evidence/s69-r3-tested-jar-classes.json)核对231份JAR类/资源，差异0。候选JAR backend/target/huankan-s69-r3-candidate-0.1.0.jar，SHA-256 a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61，未正式部署。完整隔离记录导出见本轮s69-r3-isolated-records-*.json，不以清理临时schema删除验收证据。

## 本轮真实失败与复核

|阶段日志|实际总数与错误|结果|
|---|---|---|
|[s69-r3-backend-first.log](../runtime/s69-r3-backend-first.log)|Tests run: 213, Failures: 0, Errors: 0, Skipped: 0|成功|
|[s69-r3-lifecycle-first.log](../runtime/s69-r3-lifecycle-first.log)|Tests run: 2, Failures: 0, Errors: 1, Skipped: 0|失败，已保留|
|[s69-r3-lifecycle-second.log](../runtime/s69-r3-lifecycle-second.log)|Tests run: 3, Failures: 2, Errors: 0, Skipped: 0|失败，已保留|
|[s69-r3-lifecycle-third.log](../runtime/s69-r3-lifecycle-third.log)|Tests run: 3, Failures: 0, Errors: 0, Skipped: 0|成功|
|[s69-r3-backend-final.log](../runtime/s69-r3-backend-final.log)|Tests run: 217, Failures: 0, Errors: 0, Skipped: 0|成功|
|[s69-r3-backend-final-reviewed.log](../runtime/s69-r3-backend-final-reviewed.log)|Tests run: 218, Failures: 0, Errors: 1, Skipped: 0|失败，已保留|
|[s69-r3-backend-final-complete.log](../runtime/s69-r3-backend-final-complete.log)|Tests run: 218, Failures: 0, Errors: 2, Skipped: 0|失败，已保留|
|[s69-r3-backend-verified.log](../runtime/s69-r3-backend-verified.log)|Tests run: 218, Failures: 0, Errors: 0, Skipped: 0|成功|

早期许可测试缺review字段、ECB模拟CSV字段错误、正常负载误用300ms故障超时，以及故障池第二连接未就绪导致测试准备失败，均保留原始日志/XML。最后仅改测试身份/场景与预热，不放宽生产或故障验收阈值。两次正式只读验收聚合查询5秒超时另存business-first-failure及business-query-failure，非原36条正式Java异常，不能删除或冒充成功。最终有无补测结果见部署建议。

## 实际修改文件与剩余风险

- backend/src/main/java/cn/huankan/collector/GdeltCollector.java
- backend/src/main/java/cn/huankan/collector/EcbDataCollector.java
- backend/src/main/java/cn/huankan/event/ResearchScheduler.java
- backend/src/test/java/cn/huankan/reliability/ConnectionLifecycleLoadTest.java
- backend/src/test/java/cn/huankan/reliability/EcbChunkLifecycleTest.java
- backend/src/test/java/cn/huankan/event/ResearchSchedulerLifecycleTest.java
- backend/src/test/java/cn/huankan/reliability/IsolatedReliabilityDatabase.java

新增运行/核查脚本：

- scripts/audit-s69-r3-business.mjs
- scripts/audit-s69-r3-evidence.mjs
- scripts/audit-s69-r3.mjs
- scripts/capture-s69-r3-isolated-browser.mjs
- scripts/check-s69-r3-artifacts.mjs
- scripts/check-s69-r3-clock.mjs
- scripts/cluster-s69-r3.mjs
- scripts/freeze-s69-r3-delivery.mjs
- scripts/prepare-s69-r3-browser-tests.mjs
- scripts/prepare-s69-r3-contract.mjs
- scripts/probe-s69-r3.mjs
- scripts/start-s69-r3-test.ps1
- scripts/stop-s69-r3-test.ps1
- scripts/verify-s69-r3-isolation.mjs
- scripts/write-s69-r3-reports.mjs

独立5181工作目录新增e2e-r3与playwright.r3.config.ts；本轮3份报告及统一状态报告另列于交付冻结索引。旧5180/shared frontend、历史Flyway、正式JAR未改。

剩余风险：正式16万级数据的治理扫描仍可能慢，分批数量不能保证墙钟耗时上界；API手动process(1000)仍是大事务。FRED最多1000条、NBS每响应、BLS批量入库等未因缺少该次归因证据而扩展修改，仍需容量审计。CollectorGuard会合法占用专用池连接贯穿网络I/O；采集方法及研究任务仍可长时间占用调度线程，4线程队列滞后、GC暂停未连续测量。隔离测试不证明正式磁盘/缓存/I/O等待已经消除，也不证明24小时稳定。外部NTP短测有超时，T0前必须复核。

结论：目标事务/锁放大机制的候选整改隔离验证通过；更广泛的正式底层根因、完整容量与I/O闭环仍不足。正式部署后才可能取得缺失的连续持有者证据，需用户单独批准，不能把候选通过写成正式已修复。


补充历史客户端归属：R2 socket快照49条，43884为9条，5032/36672/42328/43704各10条。日志池饱和total10与socket9并非完全同时刻；缺失连接的连续区间不能推定，不能把其他JVM的40条算作正式43884持有者，也不按当前PID信息伪造其他进程的历史JAR身份。


## SSE与Outbox完整性补测

隔离浏览器数据库复核[s69-r3-isolated-browser-database-1791948357642.json](evidence/s69-r3-isolated-browser-database-1791948357642.json)：证据253、Outbox253、重复组0、原字节哈希错误0、资格审计积压0、完整性失败0。SENT 206、浏览器接收记录206，SENT缺接收回执0；LIVE可见渲染回执3。REPLAY收到1，数据库rendered_at有效记录0：页面可见/去重断言通过，但不补造数据库渲染回执，不声称全部补读传播链已完整证明。4条FAILED均outbox_id=null、failure_kind=NETWORK_OR_CLIENT_DISCONNECT，属于连接生命周期标记；处于改订阅/断网/关上下文的测试时段，未逐条唯一映射操作，保留全部记录，不当作4条业务事件丢失。隔离SSE用同一Chromium的两个context，开发隔离和120秒探针另启动两独立Chromium；所有隔离样本均不计官方D。完整测试schema与pg_dump保留。


## S69-R3交付冻结索引

[本轮不可变副本与SHA-256清单](evidence/s69-r3-delivery-20261014T041942.json)包含候选代码/JAR、成功与失败日志、测试XML/JSON、完整隔离记录、截图和报告副本。旧R1/R2索引及原始哈希不改；冻结不代表正式部署或官方LIVE通过。
