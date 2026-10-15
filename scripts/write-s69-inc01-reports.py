from pathlib import Path
import json,datetime
root=Path(__file__).resolve().parents[1]; evidence='evidence/s69-inc01/20261014T063813Z'
def link(name,label=None):return f'[{label or name}]({evidence}/{name})'
def write(name,text):
 p=root/'docs'/name
 if p.exists():raise RuntimeError('拒绝覆盖已存在报告 '+name)
 p.write_text(text,encoding='utf-8')
now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat()
investigation=f'''# S69-INC01 正式Java异常退出调查

调查开始：2026-10-14 14:38:13（北京时间，Asia/Shanghai / UTC+08:00）；报告生成：{now}。机器 DESKTOP-JMS8B0H，Windows 10 专业版，调查账户 DESKTOP-JMS8B0H\\sthl。时钟按本机记录，未完成独立外部校时。仅事故调查、只读正式库核查和隔离预演；没有正式恢复、候选部署、5173切换、新24小时观察或S67。

**结论：正式Java 43884退出已确认；精确退出时间、退出码及最终终止机制未确认，根因未闭环。Codex应用更新导致宿主关闭已有直接系统证据，是多进程共同中断的强支持线索，但缺少宿主到43884的完整历史进程树和直接终止记录。不能将连接池饱和当作Java退出原因。**

## 1. 事故基线与保全

先读取R4正式决策、容量、慢IO、隔离预演及统一开发状态报告。Git master无任何提交，项目原有文件全部未跟踪，不能提供原版本commit SHA或以git diff还原变更；以实际JAR和冻结清单识别版本。原状态保存于 {link('git.json')}。

14:38:13—14:38:39冻结608份日志、进程JSON、脚本、报告、配置占位符和两份JAR；每份保存复制前/副本/复制后SHA-256、长度及源文件mtime，stable全部true。原R1—R4文件未覆盖。再次核验原11/130/806/1826/184份副本，共2957份，差异0。密码和令牌值不打印；配置保留变量名，调查数据不得作为公开附件发布。

入口：{link('machine.json')}、{link('capture-summary.json')}、{link('freeze-manifest.json')}、{link('artifacts.json')}。冻结清单中的源mtime仅用于文件保全，不作为退出时间。

## 2. 时间线（均为2026-10-14北京时间）

|时刻或区间|实际证据|结论与限制|
|---|---|---|
|13:15:43.444|R4历史进程快照：43884监听127.0.0.1:18080；创建2026-10-12 19:28:58.458453|最后一份带明确采样时间的已保存18080监听快照；不是最后实际监听时刻|
|13:16采样尾部|R4正式health HTTP200/UP；容量记录未为health单独写请求时间|最后取得的健康成功证据只能定位到13:16采样尾部，不能伪造毫秒|
|13:16:29.429|s70-main.log最后带时间戳条目：jvm.memory.used端点404|最后应用日志；404是监测缺口，不是退出记录|
|13:17:34.216426 / .257913|最近Outbox指向的GDELT证据入库 / Outbox创建|已提交持久化证据，不是数据库全表最后提交时刻|
|13:17:34.641979|GDELT任务SUCCESS，新增406|最后取得的有新增记录采集成功|
|13:17:38.255394|最近已有完整性检查passed|不是本轮全库字节重扫|
|13:26工具查询；返回13:26:52.950|实际Windows socket/进程查询显示43884持有10个5432连接|直接存活证据；与13:15慢事务并非连续逐请求跟踪|
|13:28:22.303846|NBS任务SUCCESS，新增0|证明一次任务完成，不证明产生新消息|
|13:31:19.340681、13:31:21.392210|stream_delivery FAILED，NETWORK_OR_CLIENT_DISCONNECT|记录在共享正式库中，未取得发起JVM的唯一关联；不能命名为43884退出|
|13:31:22.776034|正式库FED任务SUCCESS，新增0|最后业务成功证据；任务表没有保存当次Java PID，不能独立证明43884精确存活至此|
|13:31:22.813817|正式库ECB任务RUNNING开始，至本轮仍未finished|心跳为空、租约已过期；不是实际仍在执行|
|13:31:23.071054|Windows Update ID43：开始安装OpenAI.Codex更新|外部应用维护实际发生|
|13:31:23.086383|AppXDeploymentServer ID603：RegisterByPackageFamilyName含ForceTargetApplicationShutdownOption|更新操作要求关闭目标Codex应用|
|13:31:23.665|runtime/postgres.log：PID38512收到fast shutdown request|该日志属于项目55432，不能混为仍运行的5432正式共享实例|
|13:31:24.110|55432日志：后端55448 terminated by exception 0xC000013A|只属于该数据库子进程；不能移用为正式Java退出码，也未据代码数值自行断言触发者|
|13:31:24.601|隔离Java44840最后日志：55432连接closed|直接证明隔离连接失效，不能归因正式43884|
|13:31:25.720344|AppX ID646：Codex应用已关闭以进行维护|已确认宿主应用关闭|
|13:31:25.725625—.728614|Application XML：沙箱服务被要求停止、已停止|已确认服务生命周期事件；没有目标Java PID列表|
|13:31:25.729147 / .760373|AppX ID9650：旧CodexSandboxService服务已成功终止|外部维护证据，不是Java直接终止审计|
|13:31:25.804566—13:31:26.881952|SCM安装新ChatGPT服务；沙箱服务running；Windows更新成功|更新完成，不证明寰瞰业务恢复|
|13:33:04|本机Codex日志turn_trigger=app_update_resume|任务因应用更新恢复的旁证|
|13:38:24.636|工具查询原测试执行session返回Unknown process id 28387|测试会话丢失，不是正式PID退出时间|
|13:38:45.448|首次明确保存18088无监听返回|隔离服务退出已被发现|
|13:38:52.859—13:38:57.718|首次保存查询43884的空结果|首次已保存发现正式PID不在；查询结果未单独写采样毫秒|
|13:39:08.018—13:39:14.875|端口JSON只剩5432和BizPulse 5173，18080/18087等不在|首次明确18080无监听证据；不是精确退出时刻|
|14:38现场 / 14:57:32—36复核|正式health与研究health均ECONNREFUSED；无正式Java/研究/寰瞰前端监听|服务仍中断，未发现其他人员恢复|

精确退出区间不能从最后日志推定。13:31附近的多源迹象支持共同中断窗口，但不同进程逐个退出时刻未知。原前端日志只有Vite消息，没有可靠退出记录，研究服务日志在早前结束而进程13:15仍存活，直接说明“日志结束=退出”不成立。

历史入口：docs/evidence/s69-r4-processes-before.json、s69-r4-processes-interruption.json、s69-r4-capacity-20261014T051519.json；本轮 {link('interruption-history.json')}、{link('appx-update.json')}、{link('Application.json')}、{link('System.json')}、{link('final-live-check.json')}。

## 3. JVM、Windows与进程管理调查

原PID43884历史命令为Java21.0.10、Windows-ROOT信任库、huankan-s70-reviewed-0.1.0.jar、18080；真实历史cwd为项目根。历史parent=42500，与主前端43740、5176前端44516及原研究43704同父的启动线索吻合。S66研究5032/5180前端49192 parent=43660；开发61124/静态57668 parent=49556。不同组不共享已确认直接父进程；缺其父命令和上层进程树，不能声称所有进程同父或被同一脚本关闭。当前PID查询不能补造历史身份。BizPulse父53676、50392及创建时间保持。

Application12条、System25条、PowerShell Operational1344条、Windows PowerShell3569条、Defender2条、Resource Detector1条已保存原始XML及可渲染消息。AppX记录补充保全。未取得java.exe应用崩溃或Java OOM事件；Resource Detector仅“检测器启动”，14:00的node RADAR_PRE_LEAK_64不等于13:31的Java崩溃。Defender只有运行状态，没有取得隔离或终止43884记录。主机lastBoot仍2026-10-12 18:04:59.500，排除本窗口发生完整主机重启的解释；不能排除会话、作业对象或外部终止。

Security读取因未提升权限失败；Sysmon日志不存在；TaskScheduler及会话日志本窗口无匹配记录；相关计划任务筛查为空。这些均标记“未取得证据”，不证明从未发生。WER ReportArchive部分拒绝访问，CrashDumps目录不存在；Temp有3个拒绝目录。项目和可读Temp范围未找到hs_err/replay/hprof。未扩权、未改安全设置。见 {link('events-summary.json')}、{link('wer-inventory.json')}、{link('jvm-file-search.json')}、{link('scheduled-tasks.json')}。

正式stdout未检出OutOfMemoryError、致命JVM/ShutdownHook/池正常关闭标记，stderr为空；未取得43884退出码或历史GC/内存尾部。最后带SQLSTATE日志是09:04:55.967（08006），最后直接获取超时堆栈定位s70-main.log:5242；全文39个获取超时堆栈包含更早3个与R2的36个，不能把全文39改写为本次新发生39次。09:04:56日志明确“继续其他来源”，之后13:16仍提供HTTP，说明那些异常没有立即终止进程。见 {link('formal-jvm-markers.json')}、{link('analysis.json')}。

审计已冻结的启动/停止/Playwright/端口/预演脚本及10月14日R4实际工具调用。R1—R4测试关闭使用18088/5184、schema、PID创建时间、JAR路径等护栏；owned关闭定位55434真实data_directory和启动时间。未在取得的实际调用中发现13:31对43884执行Stop-Process、taskkill、按端口杀正式进程或批量清理Java。脚本定义出现停止指令不等于已经调用。旧start-services具有强制停止自己旧包的路径，start-s70-services同时起两Java和两前端，均不适合作为本轮单Java恢复入口。

本轮隔离预演遇Start-Process -Wait等待数据库子孙而阻塞：核对仅本轮包装器50132的PID/创建时间/命令后停止包装器，数据库47568保持，核对它的创建时间/目录/端口后接续。首次UTC ticks比较不一致安全拒绝，修正UTC归一化后再执行；没有误杀。此为本轮实际脚本问题，不追认它就是13:31事故原因。原失败、修复及恢复证据均保留。见 {link('isolated-wrapper-interruption.json')}、{link('isolated-rehearsal.json')}。当前执行脚本另作交付冻结，不改最初608份现场。

## 4. 因果假设矩阵

|假设|等级|依据/缺口|
|---|---|---|
|JDBC获取异常直接导致主进程终止|未确认|异常真实；线程为HTTP/调度；后来继续业务，缺main退出/容器关闭链|
|未捕获异常终止Java|证据不足|无退出码、致命堆栈、main异常尾部|
|调度异常关闭Spring|证据不足|有LoggingErrorHandler记录但无ContextClosed/关闭日志|
|JVM内存/资源异常退出|证据不足|无Java OOM/hs_err；CPU/内存历史不是终止证明|
|项目脚本误杀正式Java|未确认|审计范围内未取得实际误杀调用，不能排除缺失日志|
|Codex更新关闭宿主并连带任务树退出|有证据支持|ForceTargetApplicationShutdown、维护关闭、沙箱停止、55432关闭和app_update_resume；缺43884直接终止审计/完整祖先树|
|Windows安全软件终止|证据不足|Defender无对应终止证据，其他软件日志未取得|
|完整主机重启导致本窗口退出|已排除|boot时间未变，5432/BizPulse历史进程保持；只针对完整重启解释|
|多进程共同外部维护影响|有证据支持|跨父组窗口吻合，不能确认每个退出精确顺序及共同终止对象|

## 5. 自动审批拒绝保全

2026-10-14 13:47:35.445原工具拒绝信息含 **blocked by policy**。操作为复制并调整静态代理到55434/huankan_r4_test、启动5184静态代理并运行Playwright补测，call_id=call_1e135b2ed7e74c6f988106aa5801bc9c，R4会话文件第367行。具体策略原因未提供。完整工具操作和原始返回保存于 {link('agent-history-v2.json')}。初版agent-history.json筛选过宽且含其他项目片段，不作为归因或审批结论依据，最终以v2及原会话行号为准。

本轮工具权限为approval_policy=never，不提供可用的权限扩大/审批调用；不请求提升Security权限、不变更端口/代理名/运行账户/调用方式重试该静态代理与浏览器限制。用户侧可经正常审批/支持渠道确认具体拒绝，本轮没有取得批准，浏览器仍阻塞，拒绝原因不猜测。隔离JAR预演沿用已许可的18088/55434数据库流程，不运行任何被拒静态代理/浏览器操作。

## 6. 尚缺证据与退出调查交接

缺43884历史退出码、终止者、Windows进程终止审计、job对象成员/销毁记录、42500/43660/49556的真实历史祖先树、Java最后GC/资源指标、逐任务到PID的映射，以及确切最后健康/最后监听时刻。需要原宿主维护/进程管理方提供已有只读审计，不能通过重演生产中断获取。根因保持未闭环。

原版恢复、R3部署、5173切换、新24小时分别No-Go。具体业务风险及准入表见[业务恢复风险评估](S69-INC01-正式业务恢复风险评估.md)，拟操作与回滚见[受控恢复方案](S69-INC01-受控恢复操作与回滚方案.md)。
'''
write('S69-INC01-正式Java异常退出调查.md',investigation)
risk=f'''# S69-INC01 正式业务恢复风险评估

报告时间：{now}（北京时间）。**四项均No-Go；本轮只提交恢复准备结果，没有取得或执行正式恢复批准。** 原A/B/C失败、D等待真实官方双端LIVE（0合格样本）、36次R2超时保留。

## 1. 当前正式现场

14:38与14:57复核：18080、18087及寰瞰前端均无监听，HTTP health连接拒绝；未发现被其他人员恢复。0.0.0.0:5173仍为BizPulse PID50392，创建时间2026-10-13 16:26:05.183498未变。仅数据库可连接不能说明应用健康。

5432属于原postgresql-x64-18服务，服务控制进程4556、实际postmaster7072，目录D:/Program Files/PostgreSQL/18/data，账户NetworkService；TCP与postmaster.pid/opts及服务PathName一致。正式DB huankan、角色huankan_app、PG18.6，数据库启动2026-10-12 18:05:15.712373不变。共享正式实例与退出的runtime/postgres-data:55432明确区分。本轮未重启PG、终止他项连接或调整参数。{link('postgres-service.json')}、{link('database.json')}、{link('final-live-check.json')}。

基本只读连通、身份、51迁移和小查询可用；不能签署“全部生产容量健康”。两个合并查询及分离后的全量失败统计/无有效索引的最新证据排序在3秒statement_timeout内被取消，原错误保留，未增加时限重扫。全量当前证据完整率、最晚全表入库时间与完整I/O性能未知。缺少正式JVM池/调度指标，旧版对应端点历史404，不能预填0。

## 2. 数据、Outbox及中断任务

|项目|本轮实际结果|边界|
|---|---|---|
|Flyway|public中51条，success全部true，与R4 version/checksum一致|只读状态；没有生产迁移|
|结构约束|raw_evidence/financial_event/event_evidence/stream_outbox约束与列清单已保存|不是全库每条业务约束重算|
|历史基线抽查|84616基线中确定性抽25条，ID/来源/URL/内容哈希一致且字节SHA一致|本轮仅25条，不能冒充84616条完整重扫|
|最近Outbox证据|GDELT证据id daec3584-817b-4903-89c2-859dadf4c6de字节SHA一致|另1条真实近期证据抽查|
|最近Outbox引用|30条event/evidence存在、financial_event证据匹配、event_evidence关联存在|不是全表孤儿检查|
|既有完整性记录尾部|最近25条全部passed，最近13:17:38.255394|当前全量failed统计超时；R4于13:38取得166407条failed=0为历史快照|
|表规模|pg_stat_user_tables估计raw166407/event166401/outbox166401|统计估计，不作为本轮精确count|
|Outbox最大持久化ID|166612，created_at=13:17:34.257913|ID有间隙，不以max(id)冒充记录总量|
|LIVE_RULE_AUDIT|last_outbox=166612；实际WHERE id>cursor记录数0|仅资格审计游标口径，不等于所有浏览器都已收到|
|SSE近期记录|最近FAILED 13:31:21.392210、NETWORK_OR_CLIENT_DISCONNECT|断连证据；非证明正式JVM进程退出的记录|
|未结束采集|ECB 987eaebe-b88d-41f9-a3b2-4f50eaea6eee，13:31:22.813817 RUNNING，finished/heartbeat为空|当前没有采集进程，属于悬挂持久化状态|
|ECB租约|owner d11263d7-088a-4532-9f3e-6b5dc757ec5b，expires_at13:33:22.811825|本轮只读，未删除/续租/改状态|
|悬挂任务证据|按job_id实际count=0|未发现此任务提交raw；不能排除未入库HTTP响应丢失|
|GDELT PROCESSING|有效补查为空数组|首次聚合NULL解析错误保留，不能把无JSON当初次通过|

Outbox保留提交历史，不是一个必须删掉的队列，也没有全局“每个客户端已消费”标志。历史SSE需要凭各客户端持久化cursor/Last-Event-ID补读；没有客户端游标时不能声称零待补读。新采集已停止；审计积压0是没有待审计历史行，绝不代表新消息正常产生。见 {link('database-supplement.json')}、{link('final-live-check.json')}。

最后成功任务FED13:31:22.776034（新增0）；最后新增任务GDELT13:17:34.641979（406）；NBS13:28:22.303846、ECB13:25:51.057385、ECB_DATA12:59:15.559519分别为各来源最后成功。BLS最后失败HTTP403、GLEIF为历史成功，不混作所有来源正常。每来源记录见database.json。最新已知业务成功与13:31事故窗口只隔不足1秒，但缺所有事务的commit时间/WAL业务关联，不能宣称期间无损失。ECB未完成、停止期间未采集的新官方消息、SSE客户端游标缺失均是实际恢复风险。

## 3. 原版与候选身份及差异

|项目|原正式版本（方案A）|R3候选（方案B）|
|---|---|---|
|实际文件|D:/myAI/HUANKAN/backend/target/huankan-s70-reviewed-0.1.0.jar|D:/myAI/HUANKAN/backend/target/huankan-s69-r3-candidate-0.1.0.jar|
|SHA-256|197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77|a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61|
|包版本|Manifest 0.1.0，S70-reviewed标签|Manifest 0.1.0，R3标签|
|Java/Boot|Java21.0.10；Boot3.5.16|同一运行环境与Boot版本|
|迁移|51条|51条与原版逐字节一致，无新增|
|优点|同一已运行包，变更较小|分批事务与诊断优化，隔离有效|
|风险|已知长事务、池10饱和与慢IO；原版监测缺失；侧研究服务未恢复|生产容量/慢IO边界和浏览器恢复未验收|

R3实测与R4完整64位报告值一致；用户本轮粘贴的短串缺少中间b4a89532c片段，不能作为有效SHA-256。原→R3 ZIP非目录条目新增23、变更20、删除0，涉及采集批事务、治理研究、SSE、连接诊断、调度开关及aspectj依赖；不是简单换日志。两份当前实际包与最初冻结副本哈希一致，完整条目清单见artifacts.json。Git无提交，不虚构commit或精确源码历史差分。

最初冻结目录下原版JAR作为本轮回滚副本，完整路径D:/myAI/HUANKAN/docs/evidence/s69-inc01/20261014T063813Z/frozen/backend/target/huankan-s70-reviewed-0.1.0.jar，SHA同原版；候选亦冻结。只需原版恢复不涉及数据库迁移，拟启动禁用Flyway以避免恢复时执行DDL，必须启动前再只读比对51项，变化即阻塞。

## 4. 本轮隔离验证与未验证项

本轮沿用已核实的R4独占55434/huankan_r4_test、真实data_directory及保留schema r1_web_43e4372e54804d76874ac5460d1d056a；绑定127.0.0.1:18088，collector=false，测试池max2/min1，候选调度开关false，未恢复正式或原55432，没有静态代理/浏览器。原版不具备R3统一调度开关，可能有内置后台任务，本轮只影响隔离库。不是生产池10/真实来源条件的等效恢复。

|阶段|PID|健康就绪（北京时间）|正常退出|结果|
|---|---|---|---|---|
|原版|18420|14:54:34.287589|14:54:35.814188，exit0|health UP，18088监听归属匹配|
|R3|34972|14:54:56.480328|14:54:58.071384，exit0|health UP，数据和迁移兼容|
|回滚原版|62824|14:55:12.405692|14:55:13.895027，exit0|health UP，同一副本恢复|

三次真实数据库快照保持1原始证据、1Outbox、1job、51迁移，SHA一致；没有新造生产事件。55434本轮启动14:50:42，14:55:14完成正常停止；数据保留。18088和55434最终无监听。原版和R3启用仅health/shutdown、graceful后，认证shutdown均有效；没有执行“无令牌shutdown安全性”测试，正式端点鉴权完整门禁仍缺。{link('isolated-rehearsal.json')}及isolated-stage-1/2/3、stdout/stderr保存完整记录。

恢复脚本默认只读预检通过；传入Execute/UserApproved仍被技术No-Go拒绝，正式进程未启动；PowerShell解析无错误。PID/启动时间/路径/端口护栏用于本轮实际隔离停机，UTC不一致曾安全拒绝并修正。本轮没有人为停止共享5432、模拟正式JDBC故障或修改正式业务状态。

历史R4：后端218/前端19/隔离10通过；浏览器12通过、2失败、4未完成，保持。2失败为SSE夹具schema缺失与关注/证据测试无夹具超时；4未完成为手机S66/S64/来源/设置。原55432复测中断、55434静态补测blocked by policy。R3历史18通过单列，不替代R4，INC01未重新执行浏览器。完整真实来源采集恢复、网络SSE补读、前端页面API以及研究侧服务恢复均未在本轮通过。

## 5. 最终19项答复

|问题|结论|
|---|---|
|1 最后正常运行|43884最后直接工具存活证据为13:26窗口；health最后13:16尾部；正式库最后任务SUCCESS为FED13:31:22.776034，未唯一绑定PID|
|2 首次发现退出|PID空结果13:38:52—57；明确18080无监听13:39:08—14；精确退出时刻未知|
|3 退出原因|根因未闭环；应用更新维护关闭已确认，Java直接终止机制未确认|
|4 多进程共同原因|有强支持线索；完整祖先/job对象/终止审计缺失，不能确认全组同因|
|5 JDBC因果链|未取得；早前异常后仍存活，不能默认JDBC导致退出|
|6 PostgreSQL健康|原5432身份不变、基本只读可用；部分查询3秒超时，完整容量/IO健康未证明|
|7 原始数据完整|25历史+1近期字节抽查、30引用通过；全量当前完整性未知，R4历史保全通过|
|8 Outbox待处理|资格审计待处理实际0；已提交历史保留，客户端待补读未知|
|9 采集停止|是，无寰瞰采集JVM/监听；ECB RUNNING为悬挂状态|
|10 原JAR|S70-reviewed/0.1.0，路径与197022ff…完整哈希见上表|
|11 R3 JAR|R3/0.1.0，路径与a579d4fd…完整哈希见上表|
|12 原版风险|池饱和/长事务/慢IO、外部宿主维护、悬挂ECB、侧研究和页面未恢复|
|13 原版技术准入|No-Go：独立托管、鉴权/监测与完整业务恢复门禁未闭环|
|14 数据库迁移|不需要；51项相同，恢复拟禁用Flyway；若状态变化重新评审|
|15 可执行回滚|原版冻结副本存在且隔离重启成功；正式完整业务回滚/受控停止未验收；失败先退回停止态，不承诺完整业务恢复|
|16 已完成隔离测试|原→R3→原真实三阶段启动/正常退出与1证据/1Outbox/51迁移保全；脚本门禁/解析通过|
|17 浏览器未通过|R4 2失败、4未完成；补测策略拒绝，INC01未重跑|
|18 缺少故障证据|退出码/终止者/父祖先与job对象/最后GC/精确监听健康时刻/任务PID关联|
|19 用户批准内容|将来仅批准一个旧版18080恢复及经身份核验的失败停止；不包含侧研究、前端、R3、DB变更或24小时；当前No-Go不建议批准执行|

## 6. 独立准入决策

|操作|决定|当前理由|
|---|---|---|
|原版本受控恢复|**No-Go**|外部生命周期直接因果与独立宿主未验收、旧版监测/鉴权和完整业务恢复条件不足|
|R3候选正式部署|**No-Go**|保持R4结论，不能因事故自动升级候选；容量/慢IO/完整浏览器与回滚门禁未闭环|
|5173前端切换|**No-Go**|BizPulse通配占用，寰瞰前端已停止，浏览器补测阻塞；禁止按端口结束他项|
|新24小时回归|**No-Go**|正式业务未恢复验收、探针条件不足；不拼接历史窗口|

审批不能替代技术Go。后续需取得宿主已有进程终止证据或验证独立托管/维护隔离，验证shutdown授权拒绝与完整正常停机，补齐旧版池/调度可观测性及低负载真实采集/SSE/页面恢复门禁，再单独申请旧版18080恢复。悬挂ECB的只读现场已保全，任何状态修正/重试必须另有具体方案和批准，不清空Outbox或重跑破坏性迁移。
'''
write('S69-INC01-正式业务恢复风险评估.md',risk)
plan=f'''# S69-INC01 受控恢复操作与回滚方案

生成：{now}（北京时间）。**本文件为待批准方案；当前原版恢复No-Go，禁止执行正式启动。R3/5173/新24小时分别No-Go。** 本轮没有批准；将来用户批准也不能绕过未满足的技术门禁。

## 1. 恢复对象和最小范围

方案A只恢复原S70-reviewed/0.1.0主Java，一个进程，127.0.0.1:18080，Java21.0.10，计划账户DESKTOP-JMS8B0H\\sthl。原历史运行账户没有单独进程令牌记录，不将计划账户冒充已核实历史账户。工作目录D:/myAI/HUANKAN。

原文件D:/myAI/HUANKAN/backend/target/huankan-s70-reviewed-0.1.0.jar；恢复使用已冻结副本D:/myAI/HUANKAN/docs/evidence/s69-inc01/20261014T063813Z/frozen/backend/target/huankan-s70-reviewed-0.1.0.jar；两者SHA-256均197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77。不重新编译或从源码猜包。冻结副本已在隔离验证原版一致性。

原启动脚本scripts/start-s70-services.ps1同时启动两Java与5173/5176且会覆盖旧日志，不调用它；scripts/start-backend.ps1运行当前源码、start-services.ps1运行其他包，也不作为该恢复入口。本轮新增独立脚本scripts/start-s69-inc01-original.ps1，**默认只读预检**，Execute分支需recovery-gate.json为Go及独立宿主/关闭鉴权/业务恢复门禁全部true、真实用户批准才可进入。当前门禁为No-Go且false全部保持；实测Execute/UserApproved仍拒绝，不启动进程。相关 {link('original-preflight.json')}、{link('start-gate-test.json')}、{link('recovery-gate.json')}。

环境沿用scripts/load-local-env.ps1从Windows用户级变量加载到进程；要求HUANKAN_DB_URL精确jdbc:postgresql://127.0.0.1:5432/huankan、HUANKAN_DB_USER=huankan_app，密码/管理令牌存在且令牌至少32字符，值不得打印。SEC/FRED等原配置只核对存在和许可有效性，不启用新来源。采集开关拟true，不扩大Hikari原池上限10或调度池4。新shutdown/graceful参数为本轮隔离验证的受控停止准备，正式授权拒绝测试尚缺，因此仍No-Go。

Java必须由经过验证、不随Codex维护销毁的独立宿主启动，记录父进程、账户、创建时间和管理方式。不能宣称Start-Process/Hidden本身实现可靠脱离或Windows服务托管。当前脚本不创建计划任务或Windows服务；宿主方式需要后续具体验证，不能修改系统更新/安全设置。

方案B为R3新部署，保持No-Go：候选路径与完整SHA见风险报告；不由方案A批准自动包含。恢复主Java并不会恢复S64/S65/S66研究API和寰瞰页面；如需18085/18086/18087等侧服务，必须另列精确包/哈希/端口/连接预算和审批。BizPulse及其他应用不应受启动影响，不能取得5173、不能按java/node名称停止任何进程。

## 2. 恢复前技术门禁（全部需真实证据）

1. 保全并查明外部维护生命周期影响，或验证独立托管在宿主维护时仍符合运行设计。不得故障注入正式共享PG或重演正式中断。
2. 再检查当前监听/进程；若18080出现新监听或43884 PID被复用，记录新身份，停止按旧PID操作。查PID+UTC创建时间+完整命令/JAR+SHA+账户+监听地址/端口；缺一项不停止。
3. 再只读核验5432服务/数据目录/postmaster、PG启动时间、数据库/角色及51迁移version/checksum/success与本轮基线。迁移状态改变即阻塞；拟启动参数spring.flyway.enabled=false，因此**不执行数据库迁移**。
4. 核对冻结原版SHA、Java21、环境加载方法、源许可和管理员令牌；不打印秘密。为本次恢复建立新时间戳stdout/stderr/进程JSON，禁止覆盖s70-main.log或本轮已冻结文件。
5. 明确ECB悬挂任务和过期租约的处置：现有记录保留，旧版启动后围栏可重新取得与任务重试只是代码机制，尚未本轮实测正式恢复；不能提前改SUCCESS、删除租约/历史job。任何状态修正另行审批并保全前后差异。
6. 明确只恢复主Java时的业务范围、研究API缺口和可用用户入口；当前5173是BizPulse，不能把打开5173作为寰瞰验收。完整页面和网络SSE门禁仍需在正常获批的测试环境完成，不能绕过策略拒绝。
7. 验证shutdown端点未认证/错误令牌必须拒绝、正确令牌可正常关闭；核对监听身份后才允许端点POST。原旧进程没有这条关闭记录，隔离成功不能替代正式验收。
8. 解决旧版池/调度探针404与慢IO风险：取得可用且受认证的实际监测方案，不扩大池，不把仅health UP当通过。配置/权限变更分别审批。
9. 重新签署四项独立Go/No-Go；本轮No-Go不修改。仅原版达到Go后取得明确用户批准，另三项仍独立。

## 3. 拟执行次序（本轮未执行）

用户批准范围建议文本：**“仅允许由已验收的独立宿主、以sthI实际账户（正式执行时核对为sthl）恢复冻结SHA 197022ff…原版主Java到127.0.0.1:18080，连接原5432/huankan，不迁移数据库；若本次新进程验收失败，允许核对PID、UTC创建时间、JAR/hash及端口后按已验证认证shutdown正常停止。该批准不包括R3、研究侧服务、5173、24小时、数据库修复或强制终止。”** 审批时须引用上面的完整64位SHA，不能只用缩写。实际账户以DESKTOP-JMS8B0H\\sthl为准。

达到技术Go后，独立宿主先运行默认只读脚本，再由执行人确认真实批准，才可调用Execute/UserApproved分支。传参本身不是用户批准证据，技术门禁文件也不得为获得运行权限而擅自改成Go。脚本仅起一个原版Java、不动数据库/前端/研究进程；新日志为runtime/s69-inc01-original-<UTC毫秒时间戳>.log、-error.log、-process.json。健康、采集、回滚均写“待实测”，不预填通过。

## 4. 启动后验收清单与停止条件

|项|实际操作与通过依据|当前状态|
|---|---|---|
|18080|Get-NetTCPConnection确认127.0.0.1绑定、PID/创建时间/完整JAR身份唯一匹配|未正式执行|
|HTTP健康|health200/UP且重复低频检查无失败，保留时间/响应|未正式执行|
|JDBC|实际active/idle/pending/acquire/timeout探针或认可只读替代；不能写404为0|未正式执行，监测缺口|
|后台调度|实际启动日志、任务心跳/成功/失败/迟到；不能只看RUNNING表项|未正式执行|
|真实来源|按原许可、原计划启动；记录真实来源HTTP及新任务/入库，新增0与来源成功分开|未正式执行|
|ECB恢复|核对新租约/围栏、旧job保留、重试和新job结果，无双执行/重复证据|未正式执行|
|Outbox|读启动前166612和LIVE_RULE_AUDIT；记录新增真实提交/资格进度，历史不删除|未正式执行|
|SSE补读|以真实客户端持久化cursor/Last-Event-ID重连，实际收到REPLAY/去重/ACK；不算新LIVE|未正式执行，浏览器门禁阻塞|
|原始证据|先低负载确定性抽查ID/来源/字节SHA；新增真实记录另查；不全库压测|未正式执行|
|用户页面|合法寰瞰入口调用实际主/研究API，显示失败不能用mock掩盖|未正式执行，入口/侧服务缺口|
|共享业务|5432身份/启动时间、BizPulse 50392/5173及其他应用PID/健康保持|未正式执行，基线已保全|

启动后60秒仍无健康、身份/端口不符、Flyway尝试新增迁移、任何获取连接超时/意外JDBC失败、pending持续增长无法释放、真实来源任务持续失败/租约冲突、Outbox持久化或引用异常、证据哈希不匹配、SSE重复/漏读、其他应用出现由本操作引发的影响，都立即判验收失败。先保全新PID/启动时间/stdout/stderr/池和任务/数据库只读快照，停止准入。短时核验不是新24小时回归，也不能重置S69失败窗口。

## 5. 回滚与恢复失败处理

方案A使用原包，不做DDL，不倒灌数据库；回滚首先是经身份核验后用正确令牌POST本次18080/actuator/shutdown，保留正常关闭日志并确认PID消失和端口释放，回到已记录的事故停止态。调用前再次核对端点监听者就是本次新PID且创建时间/JAR/hash一致；错误对象或时间变化即停止操作。shutdown未就绪、认证失败、超时或进程未退出时，保留现场，报告失败并单独申请处置；禁止按端口/名称强杀、关闭整棵树或停止PG。

若可恢复包文件损坏，可从本轮冻结原版副本校验同SHA再准备启动；不得替换为R3或恢复数据库旧备份。隔离原→R3→原的1证据/1Outbox/51迁移保全通过，只证明包/数据库小样本兼容，不承诺正式全部页面/研究/采集可回滚。当前完整业务回滚仍未验收，是No-Go原因之一。

失败不删除Outbox、不清业务表、不把悬挂任务改成功；需要补采时，先提出原许可、来源断点与幂等规则、停机间隔和实际来源可回补范围，单独审批。首次无健康保留日志、最多在根因明确并再次批准后做一次受控尝试，不能无限自动重启。源网站停机期间的实时消息可能无法完整恢复，必须如实披露。

## 6. 审批与限制交接

本轮交付技术结论No-Go，**当前不建议批准执行恢复**。上述审批文本仅为准入转Go后的具体申请准备。后续可申请补齐独立宿主/关闭鉴权/完整业务门禁的验证；不得将“继续”或本轮事故调查授权解释为正式启动授权。

此前静态代理/Playwright补测自动审批拒绝已保留blocked by policy，具体拒绝原因未提供；当前approval_policy=never没有可调用扩大权限流程。该补测保持阻塞，不能通过换端口、代理名、账户或调用方式绕过。2失败/4未完成不计通过。

相关：[退出调查](S69-INC01-正式Java异常退出调查.md)、[业务风险及19项答复](S69-INC01-正式业务恢复风险评估.md)、{link('isolated-rehearsal.json')}、{link('powershell-parse.json')}、{link('start-gate-test.json')}。
'''
plan=plan.replace('sthI实际账户（正式执行时核对为sthl）','DESKTOP-JMS8B0H\\sthl账户')
write('S69-INC01-受控恢复操作与回滚方案.md',plan)
status=root/'docs/统一开发状态报告.md'
with status.open('a',encoding='utf8') as f:
 f.write(f'''\n\n## S69-INC01 正式服务中断调查与受控恢复准备（{now}）

**原版受控恢复No-Go；R3正式部署No-Go；5173切换No-Go；新24小时No-Go。** 本轮仅调查与准备，不恢复正式Java、不部署候选、不切前端、不启动24小时、不进入S67；“继续”保持本轮范围。

正式43884及研究/寰瞰前端仍退出，14:57健康连接拒绝。首次历史PID空结果13:38:52—57、明确18080无监听13:39:08—14；最后直接PID存活证据13:26窗口、health成功13:16尾部，最后正式库业务成功FED13:31:22.776034，未唯一绑定JVM。不能把最后日志13:16:29.429当精确退出时间。

13:31:23开始Codex更新，AppX明确ForceTargetApplicationShutdown及13:31:25维护关闭，沙箱服务停止/重装，项目55432同窗口fast shutdown；13:33任务app_update_resume。外部维护共同中断有强支持，正式Java终止者/退出码/历史完整祖先树仍缺，退出根因未闭环。JDBC饱和36次与退出无实际完整因果链，不默认因果。

原5432为postgresql-x64-18/实际postmaster7072，目录与启动时间保持，基本只读可用；低负载3秒上限内部分查询超时，不宣称完整生产容量健康。51Flyway全部成功且校验未变。25历史证据+1近期证据字节SHA抽查、最近30Outbox引用通过，全量当前完整性未知。LIVE_RULE_AUDIT实际待处理0、最大持久化ID166612；采集JVM不在，新采集已停止，客户端补读量未知。ECB13:31:22 RUNNING任务悬挂、租约已过期、关联raw0，未自动修复状态。

两JAR重算：原版197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77；R3 a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61；均0.1.0，迁移51逐字节一致。本轮冻结608份现场稳定副本，旧R1—R4共2957副本哈希差异0，旧文件未覆盖。

本轮仅18088/55434隔离原→R3→原三阶段健康/正常退出exit0，1证据/1Outbox/51迁移保全，已正常关闭本轮隔离Java和DB。Start-Process -Wait包装器阻塞及UTC身份核验拒绝保留，核对后仅结束本轮包装器并接续，未操作正式服务。新增单Java恢复脚本默认只读，Execute/UserApproved实测被No-Go拒绝，参数不是审批；独立宿主、鉴权、池监测和完整业务恢复未验收。

R4历史后端218/前端19/隔离10通过单列；浏览器12通过、2失败、4未完成保持，55434静态补测blocked by policy原返回保全、原因未提供，本轮未绕过/重跑浏览器。BizPulse0.0.0.0:5173 PID50392及创建时间保持。

交付：[异常退出调查](S69-INC01-正式Java异常退出调查.md)、[业务风险及19项答复](S69-INC01-正式业务恢复风险评估.md)、[待批准恢复/回滚方案](S69-INC01-受控恢复操作与回滚方案.md)。事故证据入口：{link('freeze-manifest.json')}、{link('database.json')}、{link('appx-update.json')}、{link('isolated-rehearsal.json')}、{link('recovery-gate.json')}。后续必须先补齐技术门禁再单独申请原版18080恢复；当前No-Go不建议批准执行。
''')
print('已新增三份报告并追加统一状态，未修改旧历史章节。')
