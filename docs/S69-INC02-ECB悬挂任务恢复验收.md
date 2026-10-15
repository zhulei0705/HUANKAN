# S69-INC02 ECB悬挂任务恢复验收

本轮调查时间从2026-10-15 09:57（北京时间，Asia/Shanghai，UTC+08:00）开始；报告生成2026-10-15T10:45:07.873402+08:00。Git仍无提交，不能提供commit SHA；以冻结文件和实际JAR识别版本。只执行准备、正式库低负载只读检查和55434/18088隔离验证，没有恢复正式Java、部署R3、修改正式业务记录、切换5173、注册系统任务、创建新观察窗口或进入S67。INC01、R1—R4证据与历史No-Go保留。

**结论：正式ECB任务仍悬挂，本轮未修改。新增审计恢复SQL和原版锁/幂等机制的隔离验证通过22项；不能据此声称正式任务已经恢复或ECB外网全链路已验收。**

## 1. 正式只读现场

|字段|实际值|
|---|---|
|任务ID|987eaebe-b88d-41f9-a3b2-4f50eaea6eee|
|source/status|ECB / RUNNING|
|started_at|2026-10-14 13:31:22.813817+08:00|
|独立created_at|表没有该列；不能伪造另一创建时刻|
|finished_at / heartbeat_at|NULL / NULL|
|lease_owner|d11263d7-088a-4532-9f3e-6b5dc757ec5b|
|租约acquired/heartbeat|2026-10-14 13:31:22.811825+08:00|
|租约expires|2026-10-14 13:33:22.811825+08:00；按本轮数据库clock_timestamp判断已过期|
|计数|new/fetched/duplicate/duplicate_batch/quarantine均0|
|关联raw / Outbox / official_response|0 / 0 / 0|
|ECBlast_checked_at|2026-10-09 11:49:53.278986+08:00|
|ECBetag/last_modified/last_successful_batch/retry_after_at|均NULL|
|重试次数|ECB ingestion_job无attempts/retry_count字段，不能填0冒充实测计数；该悬挂任务后没有新的ECB任务记录|

source_cursor是原版已有字段；ECB RSS不像GDELT有批次断点。原版EvidenceService只在首次建立cursor时写last_checked_at，冲突路径主要保留monitoring_started_at；ECB采集没有FED条件HTTP游标更新路径，因此旧last_checked_at不能独立证明任务与数据不一致。GDELT.attempts不能移用为ECB重试次数。

正式新采集进程不存在，5432只有诊断客户端。Outbox最大ID166612，LIVE_RULE_AUDIT游标166612，实际待审计0；不代表客户端已收齐，也不代表新消息继续采集。最近10条Outbox关联完整，两条历史真实证据字节SHA抽查通过，但没有全库完整性重扫。[formal-readonly.json](evidence/s69-inc02/formal-readonly.json)、[database-preflight-1792031225388.json](evidence/s69-inc02/database-preflight-1792031225388.json)。

## 2. 可确认与不能确认的分类

1. 当前任务没有找到已提交raw/Outbox，故“该任务已有业务证据提交但仅状态未更新”缺乏支持。
2. 可以确认中断、未完成状态及过期租约；**不能凭零提交证明事务已开始后回滚，或HTTP从未返回**，因为没有完整请求/WAL业务审计。
3. 当前任务未发现部分提交证据；机制测试另覆盖已有提交但状态未结束的情况，不能外推该真实任务部分提交过。
4. 租约行确实残留，但不能仅凭行存在认为持锁进程仍活。恢复须同时检查数据库时钟过期、任务心跳、owner和同来源advisory lock。
5. ECB游标没有可用于唯一确认完整两端点处理完成的收据；不能把旧last_checked_at强行推进到当前或改SUCCESS。

## 3. 原版真实能力和差距

原JARSHA197022ff…的CollectorGuard/EvidenceService/CollectorService字节码已从BOOT-INF解出并用javap只读保存，工具直接使用原JAR解出的类和依赖运行，未重新编译业务类或打包新JAR。

原版取得pg_try_advisory_lock(742020,hashtext(source))后更新租约并把同来源旧RUNNING标为FAILED/OWNER_LOST，然后建立新任务。会话锁仍持有时合法跳过；租约owner及有效期在证据事务中继续围栏。EvidenceService使用来源文档/修订/指纹去重，证据、事件、Outbox及对应cursor操作处于事务中；CollectorService的不同RSS端点是不同persist调用，不能承诺整轮两端点单事务。

差距：原版OWNER_LOST更新没有独立保存旧任务完整前后JSON审计，也没有每个ECB HTTP端点可恢复断点；只有“任务结束时的计数”不能替代实际提交证据。原版启动允许真实采集后会自行改旧任务状态，因此“只启动不修改任务”不成立；正式启动批准必须涵盖这类已说明写入，否则保持禁止。

字节码证据：[CollectorGuard-original-bytecode.txt](evidence/s69-inc02/CollectorGuard-original-bytecode.txt)、[EvidenceService-original-bytecode.txt](evidence/s69-inc02/EvidenceService-original-bytecode.txt)、[CollectorService-original-bytecode.txt](evidence/s69-inc02/CollectorService-original-bytecode.txt)。

## 4. 新增启动前恢复工具（本轮仅隔离使用）

scripts/inc02/recover-job.sql在调用者显式事务和精确search_path中执行，无新增迁移或正式函数安装。对指定任务：

1. 锁定唯一job行，核对完整旧job JSON；只有未结束RUNNING可进入。已处于同一审计终态则幂等返回，不重复完成时间/审计。
2. 非阻塞取得同来源事务advisory lock，拒绝仍持锁的活动采集器。
3. 锁定lease并核对owner=job.lease_owner、用数据库clock_timestamp判过期；最近2分钟任务心跳仍新鲜则拒绝。
4. 锁定该来源cursor，检查每条已提交raw对应Outbox/当前事件引用一致。计数或引用不一致则整事务回滚，交人工核查。
5. 仅将旧job的status/finished_at/error_code/error_detail改为FAILED、当前数据库时间、OWNER_LOST和说明；保留原计数、raw、事件、Outbox、游标、过期租约。
6. audit_log新增唯一用途动作INC02_RECOVER_<jobid>，target保存before、after、lease_before、cursor_before、实际raw/outbox计数及分类。NO_COMMITTED_EVIDENCE不宣称证明回滚；COMMITTED_UNFINISHED_TASK不伪装完整成功或已知剩余批次数。

Java隔离调用器强制URL为127.0.0.1:55434/huankan_r4_test和inc02_UUID schema；不会连接5432。正式提案另存 [proposed-formal-ecb-repair.sql](evidence/s69-inc02/proposed-formal-ecb-repair.sql)，含正式身份/启动时间、完整expected_job和默认ROLLBACK，**本轮未向正式执行，连“先写后回滚”的演算也没有做**。

## 5. 幂等重试、游标及历史保全

恢复不伪造金融事件、不把FAILED改SUCCESS、不清空历史任务、不删除过期租约。后续原版重新取得来源锁后可以覆盖旧过期lease并产生新job；已提交文档由原版EvidenceService去重。没有ECB逐端点重试计数或完整响应断点，重试只能按原许可重新获取RSS并依赖幂等；不能承诺恢复停机期间已从源站移除的消息。

事务失败/游标推进验证：在原版EvidenceService事务内模拟中断，检查证据/Outbox/cursor全部回滚。新增恢复SQL不推进cursor。已有提交的未结束任务保留证据、Outbox及原计数，并在审计独立记录实际数，必要时暴露计数不匹配，不擅自修正历史计数。

## 6. 隔离测试与真实限制

55434数据目录明确属于R4独占测试实例，克隆其既有1条明确标记测试回填的记录到新inc02_UUID schema；没有向正式库复制测试数据，没有新造金融内容/新增financial_event。原R4输入item_fingerprint为占位符；第二轮开始在**新克隆导入准备阶段**按原版输入生成规范指纹，未修改原已存在不可变证据、未禁用任何触发器。仅在克隆中准备FED测试许可并禁用所有来源，未发外网请求。

最终完整运行22项通过，见 [recovery-tests-r3.json](evidence/s69-inc02/recovery-tests-r3.json)：真实隔离身份、51迁移、既有1证据/1Outbox、测试许可/禁外网、ECB零提交审计恢复、重复恢复幂等、不改租约/游标/事件、审计前后完整、现场变化拒绝、未过期租约拒绝、新鲜心跳拒绝、活动来源锁拒绝、owner变化拒绝、已提交未结束任务保全、原版OWNER_LOST、原版活动锁跳过、夹具输入一致、重复保存幂等、数据/cursor原子回滚、字节/引用保持、真实池饱和超时、释放后恢复。

ECB锁/悬挂状态使用实际原版类验证；共享EvidenceService的重复输入/事务测试复用既有FED回填夹具。没有假称已做ECB两端点外网部分响应中断、真实官方消息漏采回补或正式采集调度恢复。已知原版长期池风险仍存在。

首轮在15项已完成断言后因试图调整不可变测试证据被数据库拒绝，进程exit1；该旧JSON的passed=true没有代表整轮完成，排除验收。复用已变更克隆的中间复测出现多行审计查询错误；下一轮因测试许可未设置被原版拒绝。这些失败、原日志及无效汇总均保留，最终只用新克隆的completed=true完整22项结果，不累加失败轮。见 [excluded-initial-results.json](evidence/s69-inc02/excluded-initial-results.json)。

## 7. 需要批准的正式记录与回滚办法

待审目标只有public.ingestion_job上述一个job的4列，加audit_log一条。事务须保持正式Java停止、数据库身份/启动时间相同、完整job JSON一致、owner不变且过期、无新心跳/来源锁、raw/outbox引用一致；任何变化立即ROLLBACK。正式租约、source_cursor、raw_evidence、financial_event、stream_outbox均不修改。原计数保持，不制造来源成功。

批准前仍需复核当前记录并审定最终COMMIT版本；本轮提案默认ROLLBACK，不等于授权演算。提交前异常通过事务回滚恢复全部状态与审计。提交后若发现处置错误，不自动删审计或把任务改回RUNNING：须停态保全、比对audit.after与当前job、确认没有后继任务/新租约/游标变化，再单独审批补偿4列并新增回滚审计。**该提交后补偿尚未执行/验收，不能称正式完整回滚已验证。** 优先保留真实FAILED/OWNER_LOST事实而非制造再次RUNNING的假象。
