import fs from 'node:fs';import crypto from 'node:crypto';
const read=p=>JSON.parse(fs.readFileSync(p,'utf8').replace(/^\uFEFF/,''));
const latest=prefix=>fs.readdirSync('docs/evidence').filter(x=>x.startsWith(prefix)&&x.endsWith('.json')).sort().at(-1);
const link=file=>`[${file}](evidence/${file})`,pick=prefix=>{const file=latest(prefix);if(!file)throw Error('缺少真实证据：'+prefix);return {file,data:read('docs/evidence/'+file)};};
const baseline=pick('s69-r2-baseline-'),summary=pick('s69-r2-summary-'),integrity=pick('s69-r2-formal-evidence-'),clock=pick('s69-r2-clock-'),rollback=pick('s69-r2-rollback-'),business=pick('s69-r2-business-'),cleanup=pick('s69-r2-test-cleanup-'),processes=pick('s69-r2-processes-final-');
const probeFile='s69-r2-probe-20261014T005931.json',probe=read('docs/evidence/'+probeFile),routesFile='s69-r2-browser-routes-retry-20261014T0909.json',routes=read('docs/evidence/'+routesFile),isolatedFile='s69-r2-browser-isolated-retry-20261014T0908.json',isolated=read('docs/evidence/'+isolatedFile),isolationFile='s69-r2-isolation-test-20261014T003502.json',isolation=read('docs/evidence/'+isolationFile);
const backendLog=fs.readFileSync('runtime/s69-r2-backend-final.log','utf8'),backend=[...backendLog.matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g)].at(-1);
if(!backend||!backendLog.includes('BUILD SUCCESS'))throw Error('最终后端日志未通过');
const frontend=read('docs/evidence/s69-r2-frontend-build-results.json'),hashes=read('docs/evidence/s69-r2-static-hashes.json'),jar=summary.data.candidate;
const ntpFile=fs.readdirSync('docs/evidence').filter(x=>x.startsWith('s69-r2-ntp-readonly-')).sort().at(-1),ntp=fs.readFileSync('docs/evidence/'+ntpFile,'utf8'),ntpValid=[...ntp.matchAll(/(\d\d:\d\d:\d\d), d:([+-][\d.]+)s o:([+-][\d.]+)s/g)].map(x=>({at:x[1],round_trip_ms:Number(x[2])*1000,offset_ms:Number(x[3])*1000}));
const ntpErrors=(ntp.match(/错误: 0x800705B4/g)||[]).length;
const ntpFact={file:ntpFile,valid:ntpValid,timeout_count:ntpErrors,scope:'仅接受有效服务器响应，超时后显示的非NTP占位值不作为时钟偏差'};
fs.writeFileSync('docs/evidence/s69-r2-ntp-interpretation.json',JSON.stringify(ntpFact,null,2),{flag:'wx'});
const tests=`本轮最终后端${backend[1]}项，失败${backend[2]}、错误${backend[3]}、跳过${backend[4]}；前端19项通过，类型检查退出码${frontend.types}、构建退出码${frontend.build}。最终路由${routes.stats.expected}项、隔离SSE/关注${isolated.stats.expected}项，异常均${routes.stats.unexpected}/${isolated.stats.unexpected}、跳过均${routes.stats.skipped}/${isolated.stats.skipped}；共18个不同浏览器用例最终覆盖。隔离检查${isolation.results.length}项，passed=${isolation.passed}。数据库/诊断/隐私专项28项包含在后端总数中，不重复相加。`;
const history=`首轮后端202项：0失败、4错误（MVC切片缺注册器3例、隔离迁移借连接超时1例）；中间专项28项：2失败、1错误（迁移后动态下调Hikari超时未立即生效，容量故障等待超出测试预算）。最终将迁移准备连接与300ms故障池分离，MVC缺注册器时保留缺失状态，原阈值未放宽。首轮正式候选路由计划16项，实际3项失败后停止，其余13项未执行；首轮隔离浏览器2项，1通过/1失败。失败时两端SENT/接收确认存在，一端刷新遇到500ms池等待超时，原LIVE浏览器观测缺失，后来REPLAY补读恢复；不把REPLAY计为LIVE。全量测试结束后用相同池/线程/断言门槛独立复测16+2通过，无前端代码或连接池扩容。原失败日志和JSON保留，独立Web测试schema与导出保留；数据库单元测试r1_it_*按既有用例清理策略结束清理，错误链、恢复和断言另存XML及JSON，不能称本轮一次全量全绿。`;
const windows=`原健康finished_at=2026-10-13 19:32:59.160、双浏览器finished_at=19:48:01.017（北京时间），持续86400.030/86406.784秒。A/B/C失败，D等待真实官方双端LIVE样本（0）。${summary.data.snapshot_checks.length}份冻结文件匹配，原11份源文件与冻结字节前缀全部一致。旧哈希和索引未覆盖：${link('s69-r1-final-index-20261013T115021.json')}，本轮比较见${link(summary.file)}。`;
const changed=[
'backend/pom.xml','backend/src/main/resources/application.yml','backend/src/main/java/cn/huankan/collector/CollectorGuard.java','backend/src/main/java/cn/huankan/reliability/JdbcDiagnostics.java','backend/src/main/java/cn/huankan/reliability/PoolSnapshotLogger.java','backend/src/main/java/cn/huankan/reliability/DiagnosticContext.java','backend/src/main/java/cn/huankan/reliability/ObservedDataSource.java','backend/src/main/java/cn/huankan/reliability/ObservabilityConfiguration.java','backend/src/main/java/cn/huankan/reliability/ExecutionDiagnostics.java','backend/src/main/java/cn/huankan/reliability/ReliabilityController.java','backend/src/test/java/cn/huankan/reliability/IsolatedReliabilityDatabase.java','backend/src/test/java/cn/huankan/reliability/ObservabilityDatabaseTest.java','backend/src/test/java/cn/huankan/reliability/ObservabilityPrivacyTest.java',
...fs.readdirSync('scripts').filter(x=>/s69-r2/.test(x)).map(x=>'scripts/'+x),
'docs/S69-R2-连接池与调度可观测性验收.md','docs/S69-R2-正式部署与静态前端准入报告.md','docs/S69-R2-新24小时回归预检报告.md','docs/统一开发状态报告.md'];
const changeList=changed.map(x=>'- '+x).join('\n');
const poolClock=clock.data.measurements.find(x=>x.name==='隔离Java精确Instant').pool;
const poolTable=[['maximumPoolSize','maximum'],['minimumIdle','minimumIdle'],['connectionTimeout','connectionTimeoutMs'],['validationTimeout','validationTimeoutMs'],['idleTimeout','idleTimeoutMs'],['maxLifetime','maxLifetimeMs'],['keepaliveTime','keepaliveTimeMs']].map(([title,key])=>`|${title}|${poolClock[key]}|`).join('\n');
const owners=processes.data.processes.filter(x=>[43740,49192,61124,57668,50392,43884].includes(x.processId)).map(x=>`|${x.processId}|${new Date(x.createdAt).toLocaleString('sv-SE',{timeZone:'Asia/Shanghai'})}|${x.workingDirectory}|${x.command.includes('vite')?'Vite开发服务':x.command.includes('serve-s69-static')?'冻结静态服务':'正式原Java'}|`).join('\n');
const faultRows=summary.data.formal_faults.failures.map(x=>`|${x.at.replace('2026-10-14T','').replace('+08:00','')}|${x.scope}|${x.scheduled?'调度':'请求/其他线程'}|${x.pool?.wait_ms??'未记录'}|${x.pool?.pending??'未记录'}|`).join('\n');
const clockRows=[clock.data.fastest_windows,clock.data.fastest_formal_java,...clock.data.measurements.filter(x=>!x.name.startsWith('Windows')&&!x.name.startsWith('正式Java'))].map(x=>`|${x.name}|${x.timezone}|${x.offset_interval_ms?.join('～')??'未取得'}|${x.round_trip_ms??'未取得'}|`).join('\n');
const generalRisk='直接故障机制已确认，底层根因尚未完整确认。10月13日15:56缺历史连接持有者/SQL/事务/GC记录，不能事后准确还原。R2有正式PID归属、长事务、锁和I/O实证，但慢I/O成因、全部10连接的连续持有区间及高并发容量边界尚未完整确认；不同数据库实例仍共享主机资源，测试迁移及浏览器访问可能增加负载，不能证明它们是唯一原因。候选代码未部署正式Java。';
const rootDocument=`# 寰瞰 HUANKAN · S69-R2 连接池与调度可观测性验收

本轮完成证据补强、候选诊断编码、隔离故障及全量回归。**${generalRisk}** 正式部署与新24小时仍未实施。

## 基线与证据

${windows}

${link(baseline.file)}记录实际Git状态（项目文件仍大量未跟踪，不能声称已有可供比较的已提交版本）、原JAR及R1候选哈希、低负载pg_stat_activity。原正式PID43884创建时间未变，JAR SHA256 ${summary.data.formal.sha256}；R1候选SHA256 d79735d9dbe8159f70d942f5a8a9928699df16d76ca22fabdc78c7f1c323ca28。R2候选${jar.jar}，SHA256 ${jar.sha256}。候选构建日志runtime/s69-r2-candidate-build.log的package阶段跳过测试，随后runtime/s69-r2-backend-final.log重新执行完整测试；${link('s69-r2-tested-jar-classes.json')}核对231份JAR类/资源与本轮测试使用内容，差异0。

## 新发生的正式故障

R2冻结日志检出${summary.data.formal_faults.count}条真实获取连接失败，首条${summary.data.formal_faults.first}，末条${summary.data.formal_faults.last}，其中${summary.data.formal_faults.scheduler_count}条来自调度线程；最大pending=${summary.data.formal_faults.max_pending}。错误时total=10、active=10、idle=0，约30秒等待。全部脱敏异常链、方法、池状态见${link(summary.file)}及其中raw_log哈希；SQLState/错误码原日志未输出，保持未知，不以隔离值回填。

|发生时间（北京时间）|原异常链业务位置|线程类别|等待ms|pending|
|---|---|---|---|---|
${faultRows}

08:49:59与08:50:53只读故障快照为s69-r2-fault-database-1791939000701.json、s69-r2-fault-locks-1791939053908.json；对应Windows5432客户端本地端口56526/59279/59288/61696均归属正式43884，未将其他JVM归到正式池。PG44696事务开始08:45:25.033512，持有742019；PG31256等待该锁、PG45728的collector_lease更新等待该事务。PG57784事务开始08:41:29.267769，持有742024并在治理SELECT中等待DataFileRead；08:53:53仍处于同一事务并执行research_case写入。后续脱敏SQL结构、指纹和PID见${link('s69-r2-fault-20261014T005352.json')}。08:54:46.932首次后续健康200/UP见s69-r2-health-recovery-1791939286932.json；这不是每个失败请求的重试成功或精确故障结束时间。只读jcmd Thread.print保存s69-r2-formal-thread-20261014T0857.txt，显示采集/研究线程当时实际调用栈，不推算历史GC或线程饥饿。

GDELT08:45开始的RUNNING/心跳记录与整批事务代码吻合，属于强候选；旧进程缺job UUID到PG PID直接标记，不能断言742019持有者唯一属于GDELT。事件持久化与V39通知日记共用742019，治理用742024，采集租约FOR SHARE会让心跳UPDATE等待长事务。未移除锁、缩减事实/证据写入或用跳过改善成功率。

## A—H根因候选

|候选|实际证据及结论|
|---|---|
|A 慢SQL|正式治理SELECT长时间I/O等待；隔离pg_sleep/57014真实复现；本轮5000行分页计划再次验证聚合按50行限制。I/O慢的底层存储原因未确认|
|B 长事务|上述正式08:41/08:45事务实证；隔离>1秒事务与pg_stat_activity匹配|
|C 未及时释放/泄漏|隔离持有者可见，释放后holders为空、查询恢复；真实连接泄漏未确认，合法租约持有不等于泄漏|
|D 调度并发|正式采集、治理和7次调度异常；隔离固定延迟/SSE容量竞争后恢复|
|E SSE与普通请求竞争|同一数据源；隔离真实CollectorGuard租约加批次事务占满2连接，SSE失败后恢复；网页首轮发生获取连接超时|
|F 大量历史查询|保留R1分页修复，本轮5000/50真实计划；正式旧包未包含该修复，不能称已消除风险|
|G 锁等待|正式742019、742024及transactionid证据；隔离55P03锁超时、回滚/释放后恢复|
|H 池容量不匹配|2连接隔离机制可复现；正式连接限制100/超级用户保留3，基线50客户端连接均idle。单次空闲不代表峰值容量充足，也不足以批准扩大池|

## 连接池、调度与新增诊断

实际实现HikariCP6.3.3（原运行JAR证据保持）、PostgreSQL JDBC42.7.11。正式旧包没有提供可独立读取的池MXBean/配置接口，不能把同版本默认值称正式进程实测有效值。故障total10和约30000ms为真实记录；R2同版本默认池测试测得max10/min10/30000/5000/600000/1800000/120000，仍须区分重构默认与正式有效值。正式PostgreSQL idle_session_timeout=0，采集锁连接单独3分钟；网络设备空闲超时没有记录。

下面为R2隔离JVM18088直接读取的有效值，**不是正式配置改动**：

|参数|实际隔离有效值（时间单位ms）|
|---|---|
${poolTable}

正式maximum/minimum及调度线程参数未改。测试故障池max2/min1/300ms、Web池max2/min1/500ms；Spring调度池仍4。

- 复用Actuator/Micrometer注册活动、空闲、pending、maximum及真实获取/持有/SQL/事务/调度耗时、获取超时和数据库错误计数。11个指标接口实读200，未授权metrics和reliability/status均401，见${link(clock.file)}。计数是进程内累积，不是历史全量或HTTP请求去重数；缺失序列保持缺失。
- 生成HTTP关联ID，不信任客户端传入ID；服务与调度调用保留父ID，连接每次借出有ID和持有年龄，租约心跳保留父关联。Spring代理之外的自调用/未传播异步上下文仍可能显示无上游关联，不伪造关联。
- SQL/事务计时基于真实JDBC调用与setAutoCommit/commit/rollback；SQL执行阶段不包含全部结果集消费时间，完整占用另看hold。任务耗时从方法开始到返回/异常，不包含Spring队列等待；success标签表示方法未抛出，不代表内部吞掉失败的业务任务成功，业务状态以FAILED记录和探针落盘任务状态为准。
- 普通新增诊断不输出SQL文本、参数、异常消息、凭证或请求私有字段，保留完整类型/SQLState/错误码因果、nextException、suppressed链；受控隔离故障证据保留脱敏完整堆栈。旧框架日志仍原样保存，未关闭异常。

## 隔离测试与恢复

数据库127.0.0.1:55432/huankan_test，启动身份见runtime/s69-r2-test-process.json，所有写入限独立r1_it_* / r1_web_* schema。命名沿用防误连保护，不代表R1窗口重开。只中断用例自有测试backend，没有停止PostgreSQL服务。

${tests}

覆盖真实池饱和/pending/获取超时、pg_sleep与长事务、SQL57014、55P03锁等待、实际租约+批次竞争、调度失败恢复、隔离连接57P01/08006失效恢复、Outbox原子回滚/重复幂等、SSE游标不前推与补读、失败记录保留、隐私/关联ID清理。历史完整生产峰值未复现，不能以小池机制复现替代正式10连接负载验证。逐场景触发、时间、异常、恢复及断言见${link(summary.file)}的scenarios与最终Surefire XML/日志。

${history}

测试JVM18088和静态5184已按身份检查正常关闭，仅停止本轮实例；schema与SQL导出保留，见${link(cleanup.file)}。未操作正式Java/5173/5180/5181/5182或PostgreSQL服务。

## 保全与下一可验证方案

${link(integrity.file)}：84616条基线缺失/变更0；当时原始证据${integrity.data.current_evidence_count}，既有完整性检查${integrity.data.integrity.checked}条、失败${integrity.data.integrity.failed}，Fixture${integrity.data.fixture}、审计积压${integrity.data.audit_backlog}，51迁移及历史checksum/迁移文件变化0。只核对目录和已有字节检查记录，未称重扫全部原始字节。${link(business.file)}：正式Outbox重复组${business.data.outbox_duplicate_groups}、GDELT VERIFIED=${business.data.gdelt_verified}，关注${business.data.watch_hashes.length}条仅保存行SHA，不写私有内容；事件关注复用user_watch的EVENT，未改owner密钥或SSE游标。

有界资源方案仅设计，未未经验证修改业务事务：在55432比较GDELT25/50/100行事务片及治理分页/背压的吞吐、pending、内存和PG总连接；保留742019的片内提交序、证据/Outbox原子性、租约校验、已提交行检查点。现有collector_batch_commit主键(job,batch)且不可变，不能直接反复更新充当片级回执；先设计独立追加片回执及最终批回执，再验证失败/恢复/重试不重证据、总计语义和正式迁移审批。治理SELECT需隔离真实规模EXPLAIN与索引/有界候选验证；不默认拆池、扩大池/线程、移除锁或无穷重试403。现阶段不宣称资源治理修复闭环。

## 实际修改文件

${changeList}
`;
const deploymentDocument=`# 寰瞰 HUANKAN · S69-R2 正式部署与静态前端准入报告

**候选构建与最终关键页面测试通过；完整正式部署门禁未通过，5173未切换，正式Java未部署/重启。** 候选诊断不消除原正式负载风险；${generalRisk}

## 当前版本与端口

${windows}

|PID|创建时间（北京时间）|实际目录|服务类型|
|---|---|---|---|
${owners}

完整命令、监听地址与实际PEB目录见${link(processes.file)}。5173=127.0.0.1旧Vite43740，同时bizpulse50392监听0.0.0.0:5173；5180=49192，读取原frontend；5181=61124，runtime/s69-isolation/20261013T105255/development独立源码、依赖和dist-development；5182=57668冻结静态。另一项目通配监听会影响非精确本机地址的归属，未证明所有地址都进入寰瞰，禁止按端口结束它。方案只接管明确127.0.0.1入口；须现场验证localhost/地址归属及绑定，失败就中止并恢复旧入口，不能杀bizpulse。

正式旧JAR SHA ${summary.data.formal.sha256}；R2候选${jar.jar}，SHA ${jar.sha256}。正式前端是Vite内存转换，没有可冒充静态manifest的旧版本号；以PID/创建、命令和147文件基线识别。

## 静态候选及本轮回归

候选版本20261013T105255，目录runtime/s69-releases/20261013T105255，manifest SHA ${hashes.manifest_sha256}。本轮独立源码生产重建4份public产物与冻结资源逐份一致，原frontend147份源码/配置/锁文件无变化，见${link('s69-r2-static-hashes.json')}；支持ui-projection.mjs沿用冻结哈希，未覆盖候选或旧dist。

${tests}

路由见${link(routesFile)}（1920/390、实时/官方/证据详情/宏观/S65/S66/S64/来源/设置，直接访问和刷新、无横向溢出、无pageerror）；隔离双端SSE掉线/补读、关注增删/软删除及证据详情见${link(isolatedFile)}。当前Vue是hash路由，正式深链形式/#/路径；裸/live不是已验证history模式。API经冻结Node代理18080，transmission/qualification/macro-changes/practical仍18087，/api/ui为只读投影；主SSE保留Last-Event-ID及连接ID，凭证未写入静态构建。18页测试所用服务组合不是已经部署R2正式后端，部署后仍必须复核。

本轮10项隔离检查重现5181语法错误HTTP500、Vite热重启和独立重建，候选HTTP及磁盘SHA不变，双浏览器保持可见/SSE连接，见${link(isolationFile)}；未在5173主动注入故障。中文操作控件与手机截图在该证据中，官方原文/缩写保持原样。

${history}

## 回滚镜像与操作草案（均未正式执行）

已生成真实独立旧前端147文件及锁定依赖（npm ci --offline成功）、原Java JAR哈希副本，${link(rollback.file)}。旧前端镜像${rollback.data.frontend}；旧Java镜像${rollback.data.backend}，SHA ${rollback.data.backend_sha256}。npm依赖独立，未改旧5180源码。没有宣称实际5173切换或回滚成功。

正式维护顺序：

1. 用户先分别批准Java部署/关闭重启方式和5173入口接管。旧Java当前未启用shutdown管理端点，不能把Stop-Process称正常关闭；停止方式与活动任务处置须明确，未经批准不强停、不取消SQL、不改数据源状态。
2. 维护前再核验进程PID+创建+目录+命令、旧/新SHA、141份冻结证据、连接余量、采集状态、Outbox游标/积压、正式数据/关注行SHA及时钟；出现新异常先停止维护。保持原管理密钥、DB身份和owner派生关系。
3. 只在旧正式Java已按批准方式结束后启动候选。沿用原Windows-ROOT信任库、Java21、18080及采集环境，不能由前端脚本顺带重启Java。
4. 核验新PID/创建/JAR、健康UP、受保护诊断和有效池参数、51迁移、任务恢复、业务请求、Outbox/SSE补读及用户关注，失败恢复原JAR，不回滚数据库或删失败记录。
5. Java与API/SSE稳定并另获前端批准后，只处理身份匹配的旧5173 PID43740；启动冻结静态、检查精确地址所有权、关键路由/中文/手机/双端SSE。0.0.0.0其他项目保持不动；绑定、路由或SSE失败停止新身份并恢复旧镜像。
6. 每步写实际时间、旧/新PID与创建、命令、版本/SHA、异常、决定及回滚起止和结果；这些是下一次维护必须生成的记录，不预填成功。

候选启动命令（须上述授权和旧端口释放后执行，本轮未执行）：

~~~powershell
. D:/myAI/HUANKAN/scripts/load-local-env.ps1
Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -WindowStyle Hidden -WorkingDirectory 'D:/myAI/HUANKAN' -ArgumentList @('-Dfile.encoding=UTF-8','-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar','D:/myAI/HUANKAN/${jar.jar}','--server.port=18080','--management.endpoints.web.exposure.include=health,metrics')
~~~

静态启动命令（用户确认必须是真实授权，--user-confirmed参数不能代替授权）：

~~~powershell
. D:/myAI/HUANKAN/scripts/load-local-env.ps1
node.exe D:/myAI/HUANKAN/scripts/serve-s69-static.mjs D:/myAI/HUANKAN/runtime/s69-releases/20261013T105255 5173 ${hashes.manifest_sha256} D:/myAI/HUANKAN/docs/evidence/s69-final-audit-20261013T115013.json --user-confirmed
~~~

回滚启动命令（仅在新实例按批准方式停止、对应端口释放并再核对旧SHA后执行）：

~~~powershell
. D:/myAI/HUANKAN/scripts/load-local-env.ps1
Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -WindowStyle Hidden -WorkingDirectory 'D:/myAI/HUANKAN' -ArgumentList @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar','${rollback.data.backend.replaceAll('\\','/')}','--server.port=18080')
Start-Process 'D:/Program Files/nodejs/node.exe' -WindowStyle Hidden -WorkingDirectory '${rollback.data.frontend.replaceAll('\\','/')}' -ArgumentList @('node_modules/vite/bin/vite.js','--config','vite.config.ts','--strictPort')
~~~

回滚验收：确认旧JAR SHA、镜像147文件、精确127.0.0.1:5173归属，健康、核心业务/关注、SSE连接与游标补读、数据目录/迁移/Outbox无破坏，并保留新PID及所有失败。源码镜像和命令已准备，真实正式停止/绑定/恢复验收尚未执行；回滚恢复服务不恢复或拼接旧S69窗口。

## 正式部署门禁

|门禁|状态|
|---|---|
|后端候选构建/数据库/调度/故障测试|通过本轮最终213项；历史与当前风险未闭环但有证据说明|
|前端构建/哈希/路由/SSE/双尺寸/隔离|最终回归通过；首轮失败保留|
|原证据与数据保全|通过只读范围；持续新增范围维护前再复核|
|回滚资料|真实独立镜像与命令已准备，正式回滚未执行|
|5173接管地址/其他项目|精确PID方案已准备，真实地址接管未执行|
|正式Java关闭方式/维护与用户批准|未确认、未批准，未准入|
|新24小时启动|未部署、未获单独批准，未准入|

本轮测试实例已关闭并保留schema/导出${link(cleanup.file)}。没有重启正式Java/共享PostgreSQL、切换5173或启动新窗口；未进入S67。
`;
const readinessDocument=`# 寰瞰 HUANKAN · S69-R2 新24小时回归预检报告

**尚不具备正式启动条件，未创建新24小时窗口。** 本轮为短时预检、代码和候选测试；正式部署与用户批准仍缺失。

${windows}

## 实际采样与跨进程时钟

${link(probeFile)}与同名JSONL：请求120秒短时预检，取得${probe.samples.length}份；最长整轮${Math.max(...probe.samples.map(x=>x.duration_ms)).toFixed(3)}ms，最长间隔${Math.max(...probe.samples.map(x=>x.gap_ms||0)).toFixed(3)}ms，正常采样相位错误${probe.probe_errors.length}，passed=${probe.passed}。目标5秒/上限10秒属于本轮短测，不是新正式60秒/90秒门槛。覆盖真实Java PID/创建、健康、两个数据库身份、正式采集状态/Outbox、候选有效池、双独立浏览器SSE/可见节点、CPU/内存/盘空间、日志大小/落盘及已有备份证据元数据。JSONL逐轮write+fsync，预期探针自身异常保存self_test和JSONL，验证异常可落盘后不计作服务宕机；没有删除异常。原备份仅核对记录元数据，本轮未重新备份恢复。

短测有两个独立Chromium进程/不同SSE连接ID，各采样真实节点可见；结束后关闭，不称当前仍维持24小时客户端或正式LIVE。正式采集状态来自只读任务记录，不把探针超时等同任务未执行。Spring队列滞后/GC全量覆盖仍不足，缺测阶段保持未知。

${link(clock.file)}补测复用原正式/stream/browser/clock精确Instant、隔离Java状态、Windows/PG/浏览器。首轮探针使用正式HTTP秒级Date并误写未有精确入口，后续检索确认既有接口，原记录保留、最终精度按补测，不修改正式包。以下每行是返回时间与本机请求前后形成的偏差区间，**中点不是已确认真实偏差**；Windows/正式Java各3次完整记录，按预先说明的最短往返收紧区间。

|进程|时区证据|偏差区间ms|往返ms|
|---|---|---|---|
${clockRows}

内部1秒区间门槛结果=${clock.data.internal_clock_bound_passed}。Windows原状态查询上次成功同步06:32:04、根分散7.7989324秒，只是同步状态不等于实际偏差；本轮未改时间/服务。随后只读NTP三次探测${link(ntpFile)}有效${ntpValid.length}次、超时${ntpErrors}次；有效09:13:39往返65.5258ms、估计偏移-0.8556ms、服务器根分散10.1318ms/根延迟0.0610ms，粗略绝对误差上界约43.7813ms，低于冻结1秒门槛。超时后非NTP占位巨值不计为真实偏移；单次成功不证明持续同步，维护和T0前复核网络校时可靠性。传播耗时保留客户端clockOffset/uncertainty，缺少精度时标证据不足，不填0。

## 五道门禁

|门禁|状态与实际范围|
|---|---|
|一 后端|候选构建和最终${backend[1]}项通过；含数据库/调度/容量故障/恢复/Outbox。${generalRisk}|
|二 前端|生产重建、147原文件/4产物SHA、16路由+2隔离浏览器、10隔离检查最终通过。精确5173接管/其他项目保护方案已准备，真实绑定未执行|
|三 监控|完整短时探针和两独立浏览器通过，内部时钟及一份有效NTP测量取得，冻结机制可用；不能称24小时能力已经证明，外部2次超时与GC/队列完整性风险保留，T0前复核|
|四 正式部署|启动、独立回滚镜像与验收顺序已准备；旧Java关闭方式、正式变更授权未确认，未准入|
|五 新观察窗口|正式候选未部署、部署后验证未完成、用户未批准启动，未准入|

任一必要项未满足不得启动。当前仅“测试与技术准备完成”，不宣称正式部署/正式24小时准入。所有本轮真实失败及后续恢复保留，D仍等待真实官方双端样本0。

## 下一窗口预冻结合同（未执行）

${link('s69-r2-next-window-contract.json')}，SHA256 ${summary.data.contract.sha256}。独立目录/T0/UTC及北京时间/版本/JAR和静态SHA/原始客户端ID必须在实际批准并就绪后生成，不预填开始/结束时间。

- 周期：各自独立86400秒，健康60秒单调目标采样、首尾90秒覆盖、间隔正向且≤90秒、probe error/interruption0。不能补采、拼接或自动重开。
- A：进程身份/JAR、HTTP200/UP、数据库与实际请求/任务；真实JDBC或调度失败判失败，缺少探针只判证据不足，不推断Java宕机或100%可用。
- B：采样能力及相位开始/结束/超时/调度滞后；不可放宽阈值删缺口。HTTP预算10秒，长步骤先有隔离预检。
- C：两独立客户端15秒检查，节点等待15秒、pageerror0；固定静态，不依赖5181/HMR；保留300秒传输超时、重连及恢复，重连超15秒或页面故障失败。
- 数据库：获取连接超时0、JDBC请求错误0；SQL100ms/事务1000ms为预警，不直接伪称服务失败；Outbox积压持续90秒需判异常，短暂批次积压记录实际恢复。
- 时钟：内部绝对区间和外部UTC不确定度均≤1000ms，记录往返/量化与测量来源；精度未知不能硬算LIVE传播延迟。
- 维护：T0后不允许计划维护；停止/异常不恢复为通过，也不自动延长到出现官方样本。
- D：官方真实合格非回放/回填NEW_EVENT、事件前双端在线、原始/Outbox/发送/独立接收/可见渲染/ACK完整。隔离Fixture、真实历史REPLAY和个人通知均不计正式LIVE；无样本等待，不延长稳定性期限。
- 保全：原始JSONL每轮落盘、异常立即落盘、结束以真实finished_at冻结副本与SHA、保留失败，不覆盖旧索引；只读备份记录不替代新的恢复验收。

## 回归、数据与批准边界

${tests}

${history}

${link(integrity.file)}：当时证据${integrity.data.current_evidence_count}、84616基线差异0、Fixture0、迁移51且历史变化0、已有完整性失败0；${link(business.file)}：Outbox重复组0、GDELT VERIFIED0、关注行SHA保存，正式数据未写模拟事件。SSE最终隔离2项复测通过，但首轮原LIVE观测缺失不得改成通过。${link(cleanup.file)}保留本轮测试schema/导出，正常关闭测试JVM；正式进程保持。

需要用户分别明确批准：正式Java候选部署及关闭/重启方式、5173静态接管、部署后验收完成后的新24小时启动。批准不能代替剩余技术预检。当前不代用户执行这些操作，不重启PostgreSQL，不修改历史迁移，不进入S67。S66若继续开发仍仅独立5181工作目录，旧5180共享源码不改。
`;
for(const [file,text] of [['docs/S69-R2-连接池与调度可观测性验收.md',rootDocument],['docs/S69-R2-正式部署与静态前端准入报告.md',deploymentDocument],['docs/S69-R2-新24小时回归预检报告.md',readinessDocument]])fs.writeFileSync(file,text,{flag:'wx'});
fs.appendFileSync('docs/统一开发状态报告.md',`\n\n## 2026-10-14 S69-R2交付\n\n${windows}\n\n本轮新增关联/SQL/事务/连接持有/池及调度诊断，正式旧包仍未部署。${tests}\n\nR2期间正式新发生${summary.data.formal_faults.count}次JDBC获取失败（${summary.data.formal_faults.scheduler_count}次调度），已保存PG/Windows端口映射、长事务、742019/742024锁及I/O证据，08:54:46首次后续健康UP。${generalRisk}\n\n${history}\n\n完整短时探针25份通过，时钟精确接口补测及一份有效NTP取得，超时2次保留；旧版回滚镜像及命令已准备。正式部署门禁未批准，Java关闭方式/精确5173接管未实际验收，新24小时未准入。原正式Java、5173、共享PostgreSQL未重启，旧5180/原frontend147文件未改，测试18088/5184已正常关闭并保留schema，不进入S67。\n\n详见[S69-R2连接池与调度验收](S69-R2-连接池与调度可观测性验收.md)、[正式部署与静态前端准入](S69-R2-正式部署与静态前端准入报告.md)、[新24小时预检](S69-R2-新24小时回归预检报告.md)。本轮实际文件清单及哈希交付索引由独立R2冻结清单保全，不覆盖原R1。\n`);
console.log('S69-R2三份验收报告已生成，统一状态报告已追加；正式部署和新窗口未执行。');
