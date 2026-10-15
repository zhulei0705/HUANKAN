# S69-INC01 正式Java异常退出调查

调查开始：2026-10-14 14:38:13（北京时间，Asia/Shanghai / UTC+08:00）；报告生成：2026-10-14T15:38:09.755737+08:00。机器 DESKTOP-JMS8B0H，Windows 10 专业版，调查账户 DESKTOP-JMS8B0H\sthl。时钟按本机记录，未完成独立外部校时。仅事故调查、只读正式库核查和隔离预演；没有正式恢复、候选部署、5173切换、新24小时观察或S67。

**结论：正式Java 43884退出已确认；精确退出时间、退出码及最终终止机制未确认，根因未闭环。Codex应用更新导致宿主关闭已有直接系统证据，是多进程共同中断的强支持线索，但缺少宿主到43884的完整历史进程树和直接终止记录。不能将连接池饱和当作Java退出原因。**

## 1. 事故基线与保全

先读取R4正式决策、容量、慢IO、隔离预演及统一开发状态报告。Git master无任何提交，项目原有文件全部未跟踪，不能提供原版本commit SHA或以git diff还原变更；以实际JAR和冻结清单识别版本。原状态保存于 [git.json](evidence/s69-inc01/20261014T063813Z/git.json)。

14:38:13—14:38:39冻结608份日志、进程JSON、脚本、报告、配置占位符和两份JAR；每份保存复制前/副本/复制后SHA-256、长度及源文件mtime，stable全部true。原R1—R4文件未覆盖。再次核验原11/130/806/1826/184份副本，共2957份，差异0。密码和令牌值不打印；配置保留变量名，调查数据不得作为公开附件发布。

入口：[machine.json](evidence/s69-inc01/20261014T063813Z/machine.json)、[capture-summary.json](evidence/s69-inc01/20261014T063813Z/capture-summary.json)、[freeze-manifest.json](evidence/s69-inc01/20261014T063813Z/freeze-manifest.json)、[artifacts.json](evidence/s69-inc01/20261014T063813Z/artifacts.json)。冻结清单中的源mtime仅用于文件保全，不作为退出时间。

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

历史入口：docs/evidence/s69-r4-processes-before.json、s69-r4-processes-interruption.json、s69-r4-capacity-20261014T051519.json；本轮 [interruption-history.json](evidence/s69-inc01/20261014T063813Z/interruption-history.json)、[appx-update.json](evidence/s69-inc01/20261014T063813Z/appx-update.json)、[Application.json](evidence/s69-inc01/20261014T063813Z/Application.json)、[System.json](evidence/s69-inc01/20261014T063813Z/System.json)、[final-live-check.json](evidence/s69-inc01/20261014T063813Z/final-live-check.json)。

## 3. JVM、Windows与进程管理调查

原PID43884历史命令为Java21.0.10、Windows-ROOT信任库、huankan-s70-reviewed-0.1.0.jar、18080；真实历史cwd为项目根。历史parent=42500，与主前端43740、5176前端44516及原研究43704同父的启动线索吻合。S66研究5032/5180前端49192 parent=43660；开发61124/静态57668 parent=49556。不同组不共享已确认直接父进程；缺其父命令和上层进程树，不能声称所有进程同父或被同一脚本关闭。当前PID查询不能补造历史身份。BizPulse父53676、50392及创建时间保持。

Application12条、System25条、PowerShell Operational1344条、Windows PowerShell3569条、Defender2条、Resource Detector1条已保存原始XML及可渲染消息。AppX记录补充保全。未取得java.exe应用崩溃或Java OOM事件；Resource Detector仅“检测器启动”，14:00的node RADAR_PRE_LEAK_64不等于13:31的Java崩溃。Defender只有运行状态，没有取得隔离或终止43884记录。主机lastBoot仍2026-10-12 18:04:59.500，排除本窗口发生完整主机重启的解释；不能排除会话、作业对象或外部终止。

Security读取因未提升权限失败；Sysmon日志不存在；TaskScheduler及会话日志本窗口无匹配记录；相关计划任务筛查为空。这些均标记“未取得证据”，不证明从未发生。WER ReportArchive部分拒绝访问，CrashDumps目录不存在；Temp有3个拒绝目录。项目和可读Temp范围未找到hs_err/replay/hprof。未扩权、未改安全设置。见 [events-summary.json](evidence/s69-inc01/20261014T063813Z/events-summary.json)、[wer-inventory.json](evidence/s69-inc01/20261014T063813Z/wer-inventory.json)、[jvm-file-search.json](evidence/s69-inc01/20261014T063813Z/jvm-file-search.json)、[scheduled-tasks.json](evidence/s69-inc01/20261014T063813Z/scheduled-tasks.json)。

正式stdout未检出OutOfMemoryError、致命JVM/ShutdownHook/池正常关闭标记，stderr为空；未取得43884退出码或历史GC/内存尾部。最后带SQLSTATE日志是09:04:55.967（08006），最后直接获取超时堆栈定位s70-main.log:5242；全文39个获取超时堆栈包含更早3个与R2的36个，不能把全文39改写为本次新发生39次。09:04:56日志明确“继续其他来源”，之后13:16仍提供HTTP，说明那些异常没有立即终止进程。见 [formal-jvm-markers.json](evidence/s69-inc01/20261014T063813Z/formal-jvm-markers.json)、[analysis.json](evidence/s69-inc01/20261014T063813Z/analysis.json)。

审计已冻结的启动/停止/Playwright/端口/预演脚本及10月14日R4实际工具调用。R1—R4测试关闭使用18088/5184、schema、PID创建时间、JAR路径等护栏；owned关闭定位55434真实data_directory和启动时间。未在取得的实际调用中发现13:31对43884执行Stop-Process、taskkill、按端口杀正式进程或批量清理Java。脚本定义出现停止指令不等于已经调用。旧start-services具有强制停止自己旧包的路径，start-s70-services同时起两Java和两前端，均不适合作为本轮单Java恢复入口。

本轮隔离预演遇Start-Process -Wait等待数据库子孙而阻塞：核对仅本轮包装器50132的PID/创建时间/命令后停止包装器，数据库47568保持，核对它的创建时间/目录/端口后接续。首次UTC ticks比较不一致安全拒绝，修正UTC归一化后再执行；没有误杀。此为本轮实际脚本问题，不追认它就是13:31事故原因。原失败、修复及恢复证据均保留。见 [isolated-wrapper-interruption.json](evidence/s69-inc01/20261014T063813Z/isolated-wrapper-interruption.json)、[isolated-rehearsal.json](evidence/s69-inc01/20261014T063813Z/isolated-rehearsal.json)。当前执行脚本另作交付冻结，不改最初608份现场。

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

2026-10-14 13:47:35.445原工具拒绝信息含 **blocked by policy**。操作为复制并调整静态代理到55434/huankan_r4_test、启动5184静态代理并运行Playwright补测，call_id=call_1e135b2ed7e74c6f988106aa5801bc9c，R4会话文件第367行。具体策略原因未提供。完整工具操作和原始返回保存于 [agent-history-v2.json](evidence/s69-inc01/20261014T063813Z/agent-history-v2.json)。初版agent-history.json筛选过宽且含其他项目片段，不作为归因或审批结论依据，最终以v2及原会话行号为准。

本轮工具权限为approval_policy=never，不提供可用的权限扩大/审批调用；不请求提升Security权限、不变更端口/代理名/运行账户/调用方式重试该静态代理与浏览器限制。用户侧可经正常审批/支持渠道确认具体拒绝，本轮没有取得批准，浏览器仍阻塞，拒绝原因不猜测。隔离JAR预演沿用已许可的18088/55434数据库流程，不运行任何被拒静态代理/浏览器操作。

## 6. 尚缺证据与退出调查交接

缺43884历史退出码、终止者、Windows进程终止审计、job对象成员/销毁记录、42500/43660/49556的真实历史祖先树、Java最后GC/资源指标、逐任务到PID的映射，以及确切最后健康/最后监听时刻。需要原宿主维护/进程管理方提供已有只读审计，不能通过重演生产中断获取。根因保持未闭环。

原版恢复、R3部署、5173切换、新24小时分别No-Go。具体业务风险及准入表见[业务恢复风险评估](S69-INC01-正式业务恢复风险评估.md)，拟操作与回滚见[受控恢复方案](S69-INC01-受控恢复操作与回滚方案.md)。


## 交付终态复核

2026-10-14 15:52:27（北京时间）端口/PID复核：仅原5432 PID7072和BizPulse5173 PID50392保留；正式18080/研究18087及本轮18088/55434无监听，本轮隔离进程均已退出。15:54:06正式库只读复核：启动时间不变、成功迁移51、最大Outbox与审计游标仍166612，ECB悬挂任务未变。见[最终端口](evidence/s69-inc01/20261014T063813Z/final-ports.json)、[最终数据库](evidence/s69-inc01/20261014T063813Z/database-final-short.json)；从docs报告访问时证据路径为evidence/s69-inc01/20261014T063813Z。

新增交付副本与SHA-256索引：[交付清单](evidence/s69-inc01/20261014T063813Z/delivery-manifest.json)、[冻结复核](evidence/s69-inc01/20261014T063813Z/delivery-verification.json)。没有执行正式恢复；四项No-Go保持。

