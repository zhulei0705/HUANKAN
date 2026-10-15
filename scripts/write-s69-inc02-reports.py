from pathlib import Path
import json,datetime
root=Path(__file__).resolve().parents[1];dest=root/'docs/evidence/s69-inc02'
def read(n):return json.loads((dest/n).read_text(encoding='utf-8-sig'))
def ev(n,label=None):return f'[{label or n}](evidence/s69-inc02/{n})'
now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
preflight=sorted(dest.glob('database-preflight-*.json'))[-1].name
common=f'''本轮调查时间从2026-10-15 09:57（北京时间，Asia/Shanghai，UTC+08:00）开始；报告生成{now}。Git仍无提交，不能提供commit SHA；以冻结文件和实际JAR识别版本。只执行准备、正式库低负载只读检查和55434/18088隔离验证，没有恢复正式Java、部署R3、修改正式业务记录、切换5173、注册系统任务、创建新观察窗口或进入S67。INC01、R1—R4证据与历史No-Go保留。
'''
host=f'''# S69-INC02 正式进程独立托管方案

{common}
**建议采用方案B：Windows任务计划程序托管监督器；当前只是已验证模板和隔离监督器，尚未注册/实际验收系统托管，不能宣称已独立于Codex维护。原版恢复仍No-Go。**

## 1. 原有托管调查

INC01已保存正式43884历史命令：Java21.0.10、Windows-ROOT信任库、原S70-reviewed JAR、18080，cwd为项目根，直接parent42500。原启动脚本start-s70-services.ps1由PowerShell Start-Process启动Java与前端并重定向日志，无Windows服务包装器/独立监督退出码。当前没有寰瞰Windows服务或计划任务；5432是另一个既有Windows服务。42500的历史可执行命令和完整祖先/job对象记录缺失，不能据当前PID补造“该历史父一定是Codex PowerShell”。已有会话工具调用与启动脚本支持由临时工具会话启动的解释，但精确历史祖先仍未知。

主Java/原前端、S66服务/前端和独立开发/静态服务各有不同父组。INC01系统日志直接证明2026-10-14 13:31 Codex应用更新带ForceTargetApplicationShutdown、维护关闭和沙箱服务停止，同窗口55432关闭，多组寰瞰进程随后均不在；没有43884直接终止审计。宿主生命周期依赖是需要消除的风险，不等于已确认所有子进程属于同一个job对象，也不证明Java自身崩溃。完整主机重启不符合既有boot时间。

旧方式未建立“不依赖用户登录”的托管保证；没有注销/会话关闭存活实测，不能宣称已验证注销时一定退出或一定保留。Start-Process -WindowStyle Hidden只隐藏窗口，不是独立托管证明。本轮未停止Codex或注销用户来复现生产故障。

当前基线：{ev('baseline.json')}、{ev('baseline-manifest.json')}。INC01退出调查及appx-update原证据完整保留。

## 2. 三方案比较

|评估项|A Windows服务|B 任务计划程序（建议）|C 修正现有直接启动|
|---|---|---|---|
|独立性|由SCM托管，可设计脱离终端；需正确服务实现|由系统调度服务启动非交互任务；需账户及维护独立性实测|直接父退出可以存活，但不证明脱离Codex job/会话|
|实现/权限|原java -jar不是已验证的服务实现；需专用包装器或服务代码、安装授权|系统已有组件，无需第三方安装；注册任务和批处理登录权需批准/核实|无需系统注册，但剩余生命周期风险大|
|启动|本轮不创建服务或开机项|无触发器、禁用、只允许后续手工按需启动|只能作为隔离测试方式|
|账户|专用最小权限账户需独立设计，不能默认LocalSystem|计划DESKTOP-JMS8B0H\\sthl、LeastPrivilege、Password登录|当前用户环境；依赖终端/父宿主和会话管理|
|日志/退出码|包装器需明确转发并报告真实Java退出码|监督器保存stdout/stderr、完整身份、UP时间、Java真实退出码|旧脚本缺持续监督，容易仅记录启动成功|
|停止|服务控制应映射认证优雅关闭，不能默认强杀|独立受保护停止脚本；AllowHardTerminate=false；不使用结束任务当优雅退出|改用完整8项身份+认证shutdown；禁按名/端口杀|
|自动恢复|本轮不配置服务恢复重启|模板没有RestartOnFailure；监督器不循环重启|不设置自动重启，失败保全人工处理|
|资源限制|可以另行设计job资源策略，未实施|任务模板Priority7，不是CPU/内存硬限；ExecutionTimeLimit=PT0S防默认时限中断|没有已验证资源隔离|
|安全风险|服务权限、凭证、包装器供应链需评审|密码由Windows任务机制处理，脚本不落盘；批处理权限/账户ACL仍待核实|当前用户秘密可用，但会话/工具清理风险不消失|
|共享PG|保持原5432与应用角色，勿依赖重启PG恢复Java|同左；只连接huankan，不调用pg_ctl管理正式库|同左；不能扩池掩盖慢IO|

微软服务文档要求服务程序处理ServiceMain及控制请求；不能直接把未经包装验证的java.exe命令登记后就称可正常启动/停止的服务。本项目选择B是基于现有组件和最小系统变更的工程判断。[微软ServiceMain说明](https://learn.microsoft.com/en-us/windows/win32/services/service-servicemain-function)。任务注册/运行依赖指定账户、凭据及批处理登录权限，需要以真实计划账户核验，不能以当前管理员权限代替。[微软任务安全上下文](https://learn.microsoft.com/en-us/windows/win32/taskschd/security-contexts-for-running-tasks)。模板未配置失败自动重启；未来如需启用必须单独评审。[微软RestartOnFailure定义](https://learn.microsoft.com/en-us/windows/win32/taskschd/taskschedulerschema-restartonfailure-settingstype-element)。

## 3. 已准备的可审查产物

- scripts/inc02/HUANKAN-S69-Original.task.xml：任务名拟HUANKAN-S69-Original；Password账户sthl，LeastPrivilege，无触发器，Enabled=false，IgnoreNew，AllowHardTerminate=false，无失败重启，PT0S。没有开机/登录触发器，没有修改电源/安全策略。
- scripts/register-s69-inc02-host.ps1：默认仅计划；注册分支要求独立配置批准与用户通过安全凭据界面提供正确账户，不覆盖现有任务；即便获批也只注册禁用任务，不启用或运行。本轮默认展示及拒绝路径通过，未调用Register-ScheduledTask。
- scripts/host-s69-inc02-original.ps1：Isolated模式监督原版Java、保存真实退出码，等待Java本身而非Start-Process -Wait子孙树；Formal分支本轮明确拒绝，不能拿模板注册当正式执行准入。
- scripts/inc02-process-identity.psm1：Windows原生命令行解析，精确-jar、唯一端口/用途参数；完整校验PID、UTC创建时间、实际绝对JAR、文件SHA、Java路径、GetOwner实际账户、127.0.0.1监听归属、用途标识。
- scripts/stop-s69-inc02-owned.ps1：只允许本轮原版18088，实际停止前两次核验完整身份，认证shutdown，超时保留现场不强杀。
- scripts/stop-s69-inc02-formal.ps1：待审批正式停止入口；默认只读，18080/formal-original/固定原版SHA/Java21路径/账户及8项身份全部匹配，执行另需正式停止批准。当前无正式进程，未实际正式停止。

用途标识拟-Dhuankan.process.purpose=formal-original；隔离实际标识inc02-isolated-original。JSON只是期望身份，必须和OS实时身份/实际文件哈希核对，不能凭可篡改PID文件终止进程。PID复用、JAR变化、路径含空格、UTC偏差均有明确测试。Windows检查到调用端点之间仍有极短竞态窗口，本轮通过紧邻调用前二次检查缩小风险，不声称操作系统原子进程绑定保证。

## 4. 本轮实际验证

|集合|实际结果|限制|
|---|---|---|
|8项完整身份、UTC归一化、Windows空格命令解析|11项通过|纯身份逻辑测试，没有终止真实进程|
|真实Java身份与篡改拒绝/父启动器退出|8项通过|实际54388；6个恶意期望身份调用停止执行路径均拒绝且Java保持|
|Windows PowerShell5.1兼容|同11项重跑通过|不累加为新增11种场景；新增脚本使用UTF-8 BOM|
|TaskScheduler COM读取模板与正式启动门禁|2项通过|NewTask/XmlText仅内存解析；未注册系统任务|
|独立配置注册门禁|1项拒绝测试通过|UserApproved参数不能绕过未批准gate；没有注册|
|监督器正常退出记录|1次真实Java正常停止exit0|54388于10:09:40就绪、10:13:25正常停止；parent73024监督器随后退出|

直接启动器69316已退出，监督器73024及Java54388继续存活，证明直接父启动器返回不等同Java退出；**没有证明Codex更新/job对象销毁/注销不再影响它们，也没有实际验证系统调度服务启动的祖先链。** 所以该独立性门禁仍不通过。{ev('live-identity-tests.json')}、{ev('identity-tests-windows-powershell.json')}、{ev('hosting-gate-tests-verified.json')}、{ev('registration-gate-test.json')}、{ev('original-run-exit.json')}。

## 5. 尚需批准的托管操作

第一项可单独申请：仅注册HUANKAN-S69-Original禁用、无触发器任务，指定sthl最小权限账户，由用户安全输入凭据；不启用、不运行、不启动Java、不设置开机启动、不改PG。相应XML和注册脚本已可审查。

第二项另行申请：准备仅55434/18088的系统任务隔离验证，明确临时任务名称/配置和清理范围，验证实际调度服务祖先、账户、日志/退出码、直接终端关闭和用户会话边界；不得为验证而中断正式PG/其他应用或重演生产Codex更新。本轮没有授权修改系统任务，因此不登记或运行该验证，不能用更换调用路径绕过权限。

Formal启动分支只有在上述实际托管门禁、数据库任务策略、用户明确接受历史终止未知风险后，才能重新开发/审核开放；当前脚本明确拒绝。正式Java恢复、ECB写入、正常停止范围必须之后单独批准，不由注册批准推导。
'''
ecb=f'''# S69-INC02 ECB悬挂任务恢复验收

{common}
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

正式新采集进程不存在，5432只有诊断客户端。Outbox最大ID166612，LIVE_RULE_AUDIT游标166612，实际待审计0；不代表客户端已收齐，也不代表新消息继续采集。最近10条Outbox关联完整，两条历史真实证据字节SHA抽查通过，但没有全库完整性重扫。{ev('formal-readonly.json')}、{ev(preflight)}。

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

字节码证据：{ev('CollectorGuard-original-bytecode.txt')}、{ev('EvidenceService-original-bytecode.txt')}、{ev('CollectorService-original-bytecode.txt')}。

## 4. 新增启动前恢复工具（本轮仅隔离使用）

scripts/inc02/recover-job.sql在调用者显式事务和精确search_path中执行，无新增迁移或正式函数安装。对指定任务：

1. 锁定唯一job行，核对完整旧job JSON；只有未结束RUNNING可进入。已处于同一审计终态则幂等返回，不重复完成时间/审计。
2. 非阻塞取得同来源事务advisory lock，拒绝仍持锁的活动采集器。
3. 锁定lease并核对owner=job.lease_owner、用数据库clock_timestamp判过期；最近2分钟任务心跳仍新鲜则拒绝。
4. 锁定该来源cursor，检查每条已提交raw对应Outbox/当前事件引用一致。计数或引用不一致则整事务回滚，交人工核查。
5. 仅将旧job的status/finished_at/error_code/error_detail改为FAILED、当前数据库时间、OWNER_LOST和说明；保留原计数、raw、事件、Outbox、游标、过期租约。
6. audit_log新增唯一用途动作INC02_RECOVER_<jobid>，target保存before、after、lease_before、cursor_before、实际raw/outbox计数及分类。NO_COMMITTED_EVIDENCE不宣称证明回滚；COMMITTED_UNFINISHED_TASK不伪装完整成功或已知剩余批次数。

Java隔离调用器强制URL为127.0.0.1:55434/huankan_r4_test和inc02_UUID schema；不会连接5432。正式提案另存 {ev('proposed-formal-ecb-repair.sql')}，含正式身份/启动时间、完整expected_job和默认ROLLBACK，**本轮未向正式执行，连“先写后回滚”的演算也没有做**。

## 5. 幂等重试、游标及历史保全

恢复不伪造金融事件、不把FAILED改SUCCESS、不清空历史任务、不删除过期租约。后续原版重新取得来源锁后可以覆盖旧过期lease并产生新job；已提交文档由原版EvidenceService去重。没有ECB逐端点重试计数或完整响应断点，重试只能按原许可重新获取RSS并依赖幂等；不能承诺恢复停机期间已从源站移除的消息。

事务失败/游标推进验证：在原版EvidenceService事务内模拟中断，检查证据/Outbox/cursor全部回滚。新增恢复SQL不推进cursor。已有提交的未结束任务保留证据、Outbox及原计数，并在审计独立记录实际数，必要时暴露计数不匹配，不擅自修正历史计数。

## 6. 隔离测试与真实限制

55434数据目录明确属于R4独占测试实例，克隆其既有1条明确标记测试回填的记录到新inc02_UUID schema；没有向正式库复制测试数据，没有新造金融内容/新增financial_event。原R4输入item_fingerprint为占位符；第二轮开始在**新克隆导入准备阶段**按原版输入生成规范指纹，未修改原已存在不可变证据、未禁用任何触发器。仅在克隆中准备FED测试许可并禁用所有来源，未发外网请求。

最终完整运行22项通过，见 {ev('recovery-tests-r3.json')}：真实隔离身份、51迁移、既有1证据/1Outbox、测试许可/禁外网、ECB零提交审计恢复、重复恢复幂等、不改租约/游标/事件、审计前后完整、现场变化拒绝、未过期租约拒绝、新鲜心跳拒绝、活动来源锁拒绝、owner变化拒绝、已提交未结束任务保全、原版OWNER_LOST、原版活动锁跳过、夹具输入一致、重复保存幂等、数据/cursor原子回滚、字节/引用保持、真实池饱和超时、释放后恢复。

ECB锁/悬挂状态使用实际原版类验证；共享EvidenceService的重复输入/事务测试复用既有FED回填夹具。没有假称已做ECB两端点外网部分响应中断、真实官方消息漏采回补或正式采集调度恢复。已知原版长期池风险仍存在。

首轮在15项已完成断言后因试图调整不可变测试证据被数据库拒绝，进程exit1；该旧JSON的passed=true没有代表整轮完成，排除验收。复用已变更克隆的中间复测出现多行审计查询错误；下一轮因测试许可未设置被原版拒绝。这些失败、原日志及无效汇总均保留，最终只用新克隆的completed=true完整22项结果，不累加失败轮。见 {ev('excluded-initial-results.json')}。

## 7. 需要批准的正式记录与回滚办法

待审目标只有public.ingestion_job上述一个job的4列，加audit_log一条。事务须保持正式Java停止、数据库身份/启动时间相同、完整job JSON一致、owner不变且过期、无新心跳/来源锁、raw/outbox引用一致；任何变化立即ROLLBACK。正式租约、source_cursor、raw_evidence、financial_event、stream_outbox均不修改。原计数保持，不制造来源成功。

批准前仍需复核当前记录并审定最终COMMIT版本；本轮提案默认ROLLBACK，不等于授权演算。提交前异常通过事务回滚恢复全部状态与审计。提交后若发现处置错误，不自动删审计或把任务改回RUNNING：须停态保全、比对audit.after与当前job、确认没有后继任务/新租约/游标变化，再单独审批补偿4列并新增回滚审计。**该提交后补偿尚未执行/验收，不能称正式完整回滚已验证。** 优先保留真实FAILED/OWNER_LOST事实而非制造再次RUNNING的假象。
'''
admission=f'''# S69-INC02 原版本恢复准入报告

{common}
**原版受控恢复No-Go；R3正式部署No-Go；5173切换No-Go；新24小时回归No-Go。** 本轮关闭了多项隔离门禁，但未获系统托管配置及正式写入/启动批准，不以缺失授权或未知事故证据自动放行。

## 1. 正式现场与精确版本

09:57基线与10:16终态：18080无监听，正式Java仍停止；5432实际PID7072/服务postgresql-x64-18控制PID4556、原目录D:/Program Files/PostgreSQL/18/data、启动时间2026-10-12 18:05:15.712373保持，角色huankan_app、库huankan可低负载只读连接。5173为BizPulse PID50392、创建2026-10-13 16:26:05.183498不变。没有操作其进程或端口。

|包|实际路径|实际SHA-256|
|---|---|---|
|原S70-reviewed / 0.1.0|D:/myAI/HUANKAN/backend/target/huankan-s70-reviewed-0.1.0.jar|197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77|
|R3 / 0.1.0|D:/myAI/HUANKAN/backend/target/huankan-s69-r3-candidate-0.1.0.jar|a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61|

51项正式Flyway version/checksum/success与R4冻结基线完全一致；本轮实际原JAR启动在隔离schema执行兼容校验，未新增生产迁移。原/R3 SQL资源一致的INC01证据保持。正式恢复不需要新迁移；拟正式启动前仍只读核验并禁止恢复时临时DDL。未重新打包这两份JAR、未修改业务Java源码。

正式ECB任务、过期lease与本轮只读基线完全相同，raw/outbox关联均0；最大Outbox及审计游标166612、待审计0。两条真实证据字节和10条Outbox引用抽查通过；**当前全量完整性未重扫、峰值容量/慢IO健康未证明**。没有采集JVM，新数据采集仍暂停。{ev('formal-readonly.json')}、{ev(preflight)}、{ev('final-state.json')}。

## 2. 最低恢复验证逐项结果

|要求|本轮实际证据|结论/边界|
|---|---|---|
|原版启动|54388，10:09:28创建，10:09:40健康UP，固定JAR/Java21/账户|隔离通过|
|正常退出|完整身份后认证shutdown，10:13:25 exit0，监督器记录实际Java退出码|隔离通过；无强杀|
|DB连接|隔离health/原版类真实JDBC及释放后恢复|通过；正式仅只读|
|Flyway|正式51校验未变、原版隔离51成功|兼容通过，无新迁移|
|采集初始化|collector=false、所有隔离来源disabled；原版CollectorGuard真实锁/新job租约绑定|初始化/围栏通过，未验收官方外网调度|
|ECB悬挂恢复|启动前审计SQL、旧版OWNER_LOST、过期/owner/锁/心跳保护|隔离通过；正式未修复|
|重复证据|原版EvidenceService对同一已有夹具重复保存0新增|通过；不宣称所有来源全负载|
|Outbox补读|保留1条历史回填、网络SSE收到且ACK成功|通过；不是LIVE|
|SSE连接/去重|直连18088，Last-Event-ID重连无重复，客户端主动close记录|8项HTTP集合包含；无静态代理/浏览器|
|身份保护|11纯逻辑、8实际进程验证；用途、账户、hash等篡改拒绝|本轮入口通过；旧历史脚本并未统一重构|
|失败可观察|短token真实启动失败、原因堆栈、exit1、前后job/raw/event/outbox计数不变|通过；仅覆盖此启动失败路径，不承诺所有失败无副作用|
|其他应用|5432与BizPulse实际PID/创建时间/监听保持|本次无可见进程影响；未代替BizPulse完整业务回归|
|独立系统托管|模板COM解析、注册/启动门禁、直接父启动器退出存活|实际系统任务未注册，维护/注销独立性未验收|

## 3. 本轮测试数量

|测试集合|本轮最终实际结果|统计方式|
|---|---:|---|
|完整身份与命令解析|11通过|identity-tests.json|
|真实进程身份/停止篡改/父启动器退出|8通过|live-identity-tests.json|
|原JAR类、事务与任务恢复|22通过|recovery-tests-r3.json，completed=true|
|直接HTTP/SSE、shutdown鉴权|8通过|http-tests.json|
|故障/池/日志/退出告警判定|10通过|observability-tests.json，其中真实隔离获取超时日志触发|
|托管模板COM解析、Formal启动门禁|2通过|hosting-gate-tests-verified.json|
|任务注册拒绝门禁|1通过|registration-gate-test.json|
|原版实际JMX池读取|1次成功|active0/idle2/total2/pending0；original-pool-jmx.json|
|原版正常退出|1次exit0|normal-stop.json及original-run-exit.json|
|原版真实失败启动|1次预期exit1，3项断言通过|startup-failure.json；不按3次启动计|
|Windows PowerShell5.1兼容|相同11项重复通过|单列复测，不加为新功能场景|
|脚本语法解析|本轮新增脚本解析无错|常规检查，不冒充业务测试|

前述功能/进程集合按行报告，不把历史218/19/18等测试计入。本轮有真实失败和夹具/模板准备错误，不称一次全量全绿：不可变证据更新拒绝、复用变更schema审计查询多行、缺测试许可、COM XML编码错误及PS5.1无BOM解析错误均保留。COM错误后外层继续导致旧hosting-gate-tests.json错误通过字段，已明确排除；最终仅严格错误停止的verified版本有效。修复任务XML字符串声明及本轮脚本UTF-8 BOM后实际COM和PS5.1验证通过，未改系统执行策略。

浏览器仍保留R4的12通过/2失败/4未完成；此前55434静态代理+Playwright补测blocked by policy完整原返回保留在 {ev('prior-policy-rejection.json')}，原因未提供。本轮没有重试该代理或浏览器操作。原版直接HTTP/SSE是INC02指定的后端隔离验收，不能用它宣称浏览器补测通过，不能以换入口解释为已解除原拒绝。

## 4. 已知JDBC风险及短时恢复观察方案（未来获批后才运行）

旧版历史池10/active10/idle0饱和、R2新增36次获取超时、真实长事务/锁/慢IO均保留；原版没有包含R3分批整改。本轮用2连接测试池验证超时321ms附近和释放恢复，不等价正式10连接或生产持续负载。原来HTTP池指标404问题不靠填0处理：可在后续批准的原版启动配置启用Hikari register-mbeans与JDK本地管理代理，保持原包、不开放远程JMX端口、不关闭JMX鉴权。实际隔离原版已读取HikariPool-1的active/idle/total/pending。

本地工具scripts/inc02/Inc02PoolProbe.java仅同账户attach读取已有本地JMX地址和MBean，不调用startLocalManagementAgent修改目标配置；没有地址或MBean即失败，不能填0。没有取得acquire时延分布/累计timeout指标，获取超时通过真实stdout错误链告警，限于日志持续落盘和探针正常。脚本inc02/observability.mjs只做告警判定，不启动后台观察或自动停机。

未来单独批准恢复后，拟独立短时核验10分钟：启动0—60秒就绪检查；之后每5秒记录健康、进程完整身份、active/idle/total/pending、JDBC异常新增、ECB新旧job/lease、官方任务、实际Outbox待审计数与客户端补读、SSE连接、stdout/stderr写入、监督器退出码及Windows相关事件。数据库检查必须低负载、只读、3秒超时，不运行全库字节扫描或故障注入。该方案本轮未启动，不是新24小时、不重置原S69 A/B/C失败或D0等待，也不是R3正式整改完成。

|信号|异常/介入规则|处置|
|---|---|---|
|身份变化、监听非预期|立即失败|保存现场，不向不匹配端点发送shutdown|
|健康60秒未就绪或之后连续2样本失败|恢复失败|人工核查；完整身份/单独授权后正常停止|
|任一获取连接超时/意外SQLSTATE08、调度数据库失败|立即告警并停止验收|不扩大池、不自动重启，保全JMX和DB快照|
|pending>0连续2样本、active=total且idle0连续3样本|持续竞争告警|先查真实持有路径/慢事务，不能据快照认定泄漏|
|JMX缺失、日志不落盘、退出码可读性失败|监测门禁失败|缺失不计0，停止准入|
|ECB仍旧悬挂、owner冲突、无符合计划的实际来源任务|任务恢复未通过|不删记录/租约、不伪造SUCCESS；另行处置|
|Outbox/引用/原始SHA异常、SSE重复漏读|数据恢复失败|保留持久化数据与客户端cursor；禁止清Outbox|
|Javaexit0或非0|均表示进程已停止；非0额外错误|监督器保存真实退出码；不循环拉起|
|PG身份变化或他项受影响|立即停止准入|禁止重启共享PG或结束BizPulse|

回滚：包仍原版，无DDL，不倒灌旧数据库备份。完整身份核验及已验证认证shutdown只结束本次新Java，失败回到已记录停态；超时不强杀。ECB补偿事务提交前可完整ROLLBACK；提交后的反向补偿需单独审计和批准，尚未正式验收。没有证明正式全业务（研究侧服务/前端）可回滚，因此不扩大恢复范围。

## 5. 原版门禁逐项复评

|门禁|当前结论|
|---|---|
|原版身份/SHA/Java21|通过|
|正式数据库51迁移兼容|通过；仍只读|
|ECB任务策略安全|已取得隔离通过证据；正式补偿/原版自动状态写入未获批准|
|独立生命周期可执行方案|模板/注册脚本/监督器已准备；系统任务真实账户、权限和维护独立性未验收|
|启停身份与鉴权保护|本轮隔离通过，未来正式入口仍需真实身份录入|
|健康/DB/SSE关键探针|本轮隔离通过，正式目前离线|
|旧版池风险监测/受控处理|原版JMX和告警判定通过；尚未由独立宿主持续运行验证，长期稳定性未知|
|回滚/失败处置|原版exit0/预期失败exit1/数据保全已验证；正式完整业务与提交后任务补偿仍缺|
|正式操作授权|未满足，本轮明确禁止|

原直接终止者/退出码若客观无法获取，可以一直保留未知；**不把补齐不可得历史日志作为无限前置条件**。未来若独立宿主与防误杀/监测/恢复门禁实际通过，可以以“未知历史终止风险已隔离且可检测、可受控停止”为理由提出有限原版恢复申请；仍须用户明确接受该残余风险。当前尚未实际验证独立宿主，不能提前接受或宣称已消除。

## 6. 最终15项答复

|问题|答复|
|---|---|
|1 原有托管|PowerShell Start-Process直起Java；没有寰瞰Windows服务/任务配置；历史parent42500命令未知|
|2 宿主依赖|有强风险线索；Codex维护关闭已确认，43884完整job/祖先与终止链仍未知|
|3 独立建议|方案B任务计划程序监督器、sthl最小权限、默认禁用无触发器、不自动恢复|
|4 误杀防护|本轮完整8项身份、固定用途/包/端口和鉴权停止通过；未改造全部旧脚本，后续正式仅允许受保护入口|
|5 ECB真实状态|同一RUNNING悬挂、心跳NULL、租约过期、raw/Outbox/官方响应0；没有新采集进程|
|6 ECB逻辑验收|隔离22项通过；共享持久化幂等用既有FED夹具，非ECB外网两端点全链验收|
|7 原包DB兼容|实际同SHA，51迁移一致，原版隔离启动通过；无需新迁移|
|8 正式数据安全|本轮未写，ECB前后相同；抽查通过，全量完整性/停机漏采不作保证|
|9 JDBC已知风险|池饱和/长事务/慢IO仍在；本地JMX+日志告警已隔离验证，不是R3修复|
|10 原版技术门禁|No-Go：实际独立托管未验收，正式写入/启动未批准，正式完整回滚/长期风险仍有边界|
|11 R3|No-Go，独立门禁保持；没有启动或部署候选|
|12 5173|No-Go，BizPulse实际占用，不停止/抢占|
|13 新24小时|No-Go，正式服务未恢复且没有批准；短时方案也未正式运行|
|14 历史缺口|43884终止者/退出码、父祖先/job对象、精确最后监听/健康、最后GC/内存仍未知|
|15 用户批准|先单独批准禁用任务注册及隔离系统托管验证；之后另审单job补偿、残余风险接受、原版18080启动/失败正常停止；不包含R3/前端/24小时|

## 7. 本轮结论与下一步具体申请

|操作|Go/No-Go|
|---|---|
|原版受控恢复|**No-Go**|
|R3候选正式部署|**No-Go**|
|5173切换|**No-Go**|
|新24小时回归|**No-Go**|

可先批准的最小下一步为：**仅注册HUANKAN-S69-Original禁用、无触发器任务，使用sthl最小权限凭据；不启用、不运行、不启动正式Java、不改PG、不设置开机启动。** XML及注册拒绝保护已准备。实际隔离系统托管验证仍须列明任务/账户/生命周期操作后单独批准。当前Formal分支明确关闭，用户“继续”不构成正式恢复授权。

正式ECB写入提案只有上述单job4列和一条审计，已提供完整旧JSON、锁/过期/心跳/引用条件及默认回滚文件，待另审；正式Java启动及失败时正常停止还需覆盖原版自动OWNER_LOST写入并接受历史终止未知风险。需要额外账户批处理权限、凭据或任务配置时由正常系统审批执行，不自动提升权限或放宽安全策略。

此前自动审批拒绝blocked by policy原因未提供，未取得该静态代理/浏览器补测批准，继续阻塞。当前执行权限没有可调用的扩大审批流程；本轮没有换端口/脚本/账户/调用方式重试同一被拒操作。后续应通过正常权限审查/支持渠道确认该操作是否允许，不能猜测隐藏策略。

交付入口：{ev('delivery-manifest.json','本轮冻结清单')}；{ev('preservation-check.json','旧证据保全核验')}。关联[独立托管方案](S69-INC02-正式进程独立托管方案.md)、[ECB恢复验收](S69-INC02-ECB悬挂任务恢复验收.md)。
'''
for name,text in [('S69-INC02-正式进程独立托管方案.md',host),('S69-INC02-ECB悬挂任务恢复验收.md',ecb),('S69-INC02-原版本恢复准入报告.md',admission)]:
 p=root/'docs'/name
 if p.exists():raise RuntimeError('拒绝覆盖已有INC02报告')
 p.write_text(text,encoding='utf8')
with (root/'docs/统一开发状态报告.md').open('a',encoding='utf8') as f:
 f.write(f'''\n\n## S69-INC02 正式恢复门禁整改与进程独立托管（{now}）

**原版恢复No-Go；R3正式部署No-Go；5173切换No-Go；新24小时No-Go。** 本轮未启动正式Java、未部署R3、未写正式业务记录、未改系统任务/服务、未动BizPulse、不切5173、不创建新观察窗口、不进S67。既有事故证据及A/B/C失败、D0等待保持。

正式18080仍无监听；5432服务/postmaster7072和启动时间保持，51迁移与冻结校验一致；两份JAR实际SHA保持原197022ff…/R3 a579d4fd…（完整哈希见INC02报告）。正式ECB同一987eaebe…任务RUNNING、心跳NULL、旧租约过期，raw/响应/Outbox0，前后只读核验未变；最大Outbox与资格审计cursor166612、待审计0，不代表新采集仍工作。

原JAR字节码及实际类隔离调用确认OWNER_LOST/会话锁/证据幂等已存在，缺完整旧状态审计。本轮新增单任务审计恢复SQL，过期/心跳/owner/活动锁/现场CAS/引用/幂等保护22项完整隔离通过；正式补偿仅待审提案，未执行。测试只复制既有1条隔离回填记录，新克隆导入准备规范化夹具指纹，未放宽不可变约束或新造金融事件。首轮和中间失败保留，不冒充一次全绿。

独立托管建议为Windows任务计划程序，已有禁用/无触发器/不自动重启模板、注册计划脚本、监督器及8项完整进程身份模块。11身份逻辑/8实际保护/8HTTP-SSE/10告警判定/2托管门禁通过，另注册拒绝、原版JMX真实读取、正常exit0及预期启动失败exit1分别验证；WindowsPS5.1重跑相同11项单列不累加。原版正常启动Java54388并认证关闭，错误令牌shutdown401，SSE仅BACKFILL历史补读并ACK、重连无重复；不计LIVE或浏览器补测。

直接启动器退出后监督器/Java仍在的测试通过，但没有注册系统任务、未验收Codex维护/job对象/注销独立性；不能称独立托管已完成。原版池风险仍在，隔离本地JMX取得active/idle/total/pending，实际获取超时日志触发告警；没有正式/长期观察。短时恢复观察方案仅编制未运行，不替代24小时或R3整改。

本轮隔离18088/55434已正常关闭并保留schema导出，最终1raw/1event/1Outbox、51迁移及原始SHA保全，PG/BizPulsePID与创建时间保持。历史终止者/退出码/完整祖先仍未知；未来如通过实际独立托管和故障保护，可按明确残余风险理由交用户确认，不要求编造历史证据。

交付：[独立托管方案](S69-INC02-正式进程独立托管方案.md)、[ECB恢复验收](S69-INC02-ECB悬挂任务恢复验收.md)、[原版准入及15项答复](S69-INC02-原版本恢复准入报告.md)、[证据清单](evidence/s69-inc02/delivery-manifest.json)。当前仅可申请先注册禁用任务及另行隔离系统托管验证；正式单job写入、原版启动/失败正常停止与未知风险接受继续分别待批准。

此前静态代理/浏览器blocked by policy原返回保留、原因未提供，仍阻塞；本轮不绕过，R4浏览器12通过/2失败/4未完成保持。
''')
print('已新增INC02三份报告并追加统一状态。')
