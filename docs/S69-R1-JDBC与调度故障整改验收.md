# 寰瞰 HUANKAN · S69-R1 JDBC与调度故障整改验收

核查时间2026-10-13 19:50:13.882。原两个窗口已最终冻结。 A服务连续性=失败；B采样完整性=失败；C浏览器可用性=失败；D官方真实双浏览器LIVE=等待真实样本（0份合格样本）。

已修复独立复现的分页无界投递聚合、数据库错误误归HTTP超时、SSE接收后立即关闭导致发送记录丢失风险，并补充调度/连接池诊断。**历史15:56池饱和的具体连接持有者与SQL/事务仍无独立历史证据，不能宣称最终根因已闭环或正式运行故障已消除。候选代码未部署正式Java。**

## 原窗口和不可变证据

|窗口|开始（北京时间）|实际结束（北京时间）|实际持续时长|
|---|---|---|---|
|健康|2026-10-12 19:32:59.130|2026-10-13 19:32:59.160|86400.03秒|
|双独立浏览器|2026-10-12 19:47:54.233|2026-10-13 19:48:01.017|86406.784秒|

预定结束分别2026-10-13 19:32:59.130、19:47:54.233；浏览器实际计时从双端初始化完成后开始，最终只认可finished_at。四项标准、原102.457/98.676秒空档、三次浏览器异常、JDBC失败及全部原始日志见[s69-final-audit-20261013T115013.json](evidence/s69-final-audit-20261013T115013.json)。最终证据索引[s69-r1-final-index-20261013T115021.json](evidence/s69-r1-final-index-20261013T115021.json)包含原11份冻结副本和SHA256，不覆写任何旧哈希。

## 真实JDBC异常与证据边界

[s69-r1-diagnosis-20261013T094157.json](evidence/s69-r1-diagnosis-20261013T094157.json)保存脱敏完整异常链。正式运行JARHikariCP6.3.3、PostgreSQL JDBC42.7.11，直接取自原JAR；原application.yml冻结为evidence/s69-r1-running-application.yml.txt。

|发生时间（北京时间）|请求/任务与实际作用域|SQLException与池状态|SQLState/错误码/事务|恢复证据|
|---|---|---|---|---|
|15:56:01.860|主Java43884，POST /api/events/query，QueryService.events|SQLTransientConnectionException，等待30021ms；total10 active10 idle0 waiting11|原日志未记录SQLState与实际错误码；获取连接失败，SQL未确认执行，其他连接事务未知|15:56:48.082首次自动健康UP，不等同本请求重试成功|
|15:56:02.354|主Java43884，scheduling-4，StreamService.publish→cursor；是SSE发布调度，并非已证实采集调度停止|CannotGetJdbcConnectionException→SQLTransientConnectionException，30005ms，waiting10|同上；待执行为Outbox/通知游标MAX查询，未获取连接|首次后续健康UP；采集FED15:57:26.389662开始并15:57:27.065670成功另有实际记录|
|15:56:02.470|主Java43884，POST /api/stream/browser/status，BrowserObservationService.status|SQLTransientConnectionException，30007ms，waiting9|原SQLState、错误码、当时事务未知；首个状态查询获取连接失败|15:56:48.082 UP、15:57:54.136继续UP|
|10:16:37.624|旧S65开发实例47496、18086，不是主Java；MacroChangeService.catalog|BadSqlGrammarException→PSQLException，无法确定参数$1类型|历史未输出SQLState/错误码；隔离同SQL复现42P18/0；不能反写成历史原值|现存代码此前已加?::text，本轮隔离null源筛选测试通过；未重复开发S65|

15:55:37.453是健康采样开始时间，随后10秒客户端超时；Java15:56:08.848记录数据库健康检查耗时31069ms。错误抛出时间减等待时长只能估计连接等待区间，不能冒充实际HTTP开始时间或SQL执行区间。邻近数据库连接/健康记录：

~~~json
[
  {
    "at": "2026-10-13T07:53:18.951Z",
    "http": 200,
    "status": "UP",
    "error": null,
    "database_connections": 51,
    "backlog": 0
  },
  {
    "at": "2026-10-13T07:54:20.887Z",
    "http": 200,
    "status": "UP",
    "error": null,
    "database_connections": 55,
    "backlog": 0
  },
  {
    "at": "2026-10-13T07:55:37.453Z",
    "http": null,
    "status": null,
    "error": "The operation was aborted due to timeout",
    "database_connections": null,
    "backlog": null
  },
  {
    "at": "2026-10-13T07:56:48.082Z",
    "http": 200,
    "status": "UP",
    "error": null,
    "database_connections": 53,
    "backlog": 0
  },
  {
    "at": "2026-10-13T07:57:54.136Z",
    "http": 200,
    "status": "UP",
    "error": null,
    "database_connections": 51,
    "backlog": 0
  },
  {
    "at": "2026-10-13T07:58:55.958Z",
    "http": 200,
    "status": "UP",
    "error": null,
    "database_connections": 51,
    "backlog": 0
  }
]
~~~

故障前GDELT15:50:21—15:51:22成功，FED/ECB15:52成功；之后FED/ECB15:57、NBS15:57:29—15:58:17成功，详见[s69-r1-formal-evidence-20261013T111718.json](evidence/s69-r1-formal-evidence-20261013T111718.json)。这些记录证明对应任务真实执行，不证明盲区全程可用。没有历史pg_stat_activity/锁/SQL耗时快照、线程转储或GC日志；pg_stat_statements未安装，log_lock_waits关闭、慢SQL日志阈值未启用。当前50条空闲JDBC连接不能倒推15:55的持有者。

## 连接池与容量

|参数|整改前证据|整改后|
|---|---|---|
|maximumPoolSize|10，正式故障日志证实|不改变正式数值|
|minimumIdle|10，同版本默认值隔离实测，正式进程未提供JMX独立读数|不改变正式数值|
|connectionTimeout|30000ms，正式30021/30005/30007ms等待及同版本实测|不改变正式数值|
|validationTimeout|5000ms，同版本隔离实测|不改变正式数值|
|idleTimeout|600000ms，同版本隔离实测|不改变正式数值|
|maxLifetime|1800000ms，同版本隔离实测|不改变正式数值|
|keepaliveTime|120000ms，同版本隔离实测|不改变正式数值|
|leakDetectionThreshold|0，同版本默认值；原日志未出现泄漏警告不能排除泄漏|不改变正式数值|

本轮不增连接池或调度线程池，Spring调度池仍4。只有新包增加ApplicationName=huankan-端口以关联数据库会话，并每30秒记录无需借连接的Hikari活动/空闲/pending快照。隔离故障测试显式max2/min1/connectionTimeout300ms；独立Web JVM为max2/min1/500ms，只是可控故障复现配置，不是正式配置。PostgreSQL正式max_connections=100、保留超级用户3；17:41只读查询50条同库空闲JDBC连接、当前容量查询见诊断，均不是故障时容量。默认idle_session_timeout为0；采集互斥连接单独设3分钟，心跳10秒。网络设备空闲超时未取得独立记录，不能声称不存在冲突。

## 最小整改和实际差异

- QueryService先MATERIALIZED选定50条，再按页聚合投递时间，筛选/排序/总数语义保留。5000条隔离数据原聚合5000次，修复后50次；本次查询计划执行39.797ms→11.014ms。这是已证实性能风险，仍不是历史30秒池饱和的唯一根因证明。修复前失败用例、计划和源码SHA保留s69-r1-query-before.log、s69-r1-query-before-20261013-1823.xml、s69-r1-queryservice-before-20261013-1823.java.txt。
- CollectorGuard先按SQLException分类连接/SQL超时/事务错误，不把JDBC timed out归为HTTP REQUEST_TIMEOUT；没有改403限次预算、互斥/租约和证据提交约束。心跳失败不再静默，锁合法跳过记录SKIPPED_LOCK_BUSY，不作为成功采集。
- CollectorService记录调度开始/结束、候选查询数据库失败、来源调用未完成和实际任务成功/失败。轮次结束明确不等同全部成功。JdbcDiagnostics记录SQLState、错误码、完整异常链与池状态，按字段和实际环境秘密脱敏；不额外获取数据库连接、不自动重试业务写入。
- StreamService发送时绑定连接ID，收到后立即关闭仍保留SENT记录，已关闭客户端不会重新放回活动表。原独立复现SENT0条、修复后1条，Outbox仍1条；对应测试与原失败日志保留。SSE游标获取失败原样抛出并记调度诊断，保留Spring后续调度与恢复行为。

|状态类别|可核对证据|
|---|---|
|任务真正失败|ingestion_job FAILED及TASK_FAILED/异常链；原失败不删除|
|未正常调度|新包SCHEDULER_STARTED/ROUND_FINISHED结合源到期与实际任务；仅缺日志或旧空档仍判未知|
|合法互斥跳过|SKIPPED_LOCK_BUSY；不生成任务，不改善成功率分母|
|调度运行但数据库不可用|SCHEDULER_DATABASE_UNAVAILABLE及获取连接异常；实际原故障就是SSE调度|
|探针失败致任务未知|相位error与独立任务记录区分；不将探针错误直接判采集失败|

## 隔离复现与完整回归

实际实例127.0.0.1:55432/huankan_test，PostgreSQL18.6，启动2026-10-12 18:46:48.623983；每个数据库用例使用独立r1_it_* schema，核对数据库和端口后才写Fixture。仅pg_terminate_backend本用例取得的PID且限定库/用户/ApplicationName，未停止PostgreSQL服务。

覆盖连接获取失败、连接中断、池pending与超时、SQL57014超时、事务/Outbox原子回滚、采集503后重试、重复幂等、租约冲突、数据库恢复后实际固定延迟任务恢复、SSE不前推游标/恢复发送、正常组件/JVM关闭恢复、失败记录保留、历史42P18与默认配置测量、接收后立即关闭竞态。数据库专项14项+诊断2项+分页1项共17项包含在完整后端总数中，不重复相加。

后端完整202项、失败0、错误0、跳过0；前端完整19项；关键浏览器18项通过、0项失败；隔离检查10项全部通过；最终候选JVM/双端网络SSE恢复通过。

最终日志runtime/s69-r1-backend-final.log、runtime/s69-isolation-20261013T105255-unit-tests.log，[s69-r1-browser-20261013T111446.json](evidence/s69-r1-browser-20261013T111446.json)、[s69-isolation-test-20261013T111816.json](evidence/s69-isolation-test-20261013T111816.json)、[s69-r1-jvm-sse-20261013T113202.json](evidence/s69-r1-jvm-sse-20261013T113202.json)。最终候选JAR backend/target/huankan-s69-r1-reviewed-final-0.1.0.jar，SHA256 d79735d9dbe8159f70d942f5a8a9928699df16d76ca22fabdc78c7f1c323ca28；正式原JAR仍197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77，未部署、未重启。首轮后端测试配置/时间型幂等假设错误、旧18项浏览器14/4及15/3、旧ACK400、JVM校验脚本415/Windows等待问题和短测积压断言失败全部保留。最终18项浏览器10分钟分步执行通过，不放宽120/min服务限流；旧连接失败缺当次完整HTTP响应证据，不能只根据复测正常把根因写成已证实限流。

探针独立60秒13份、最长整轮224.999ms，测每步骤与单调时钟目标调度，见[s69-r1-probe-20261013T111826.json](evidence/s69-r1-probe-20261013T111826.json)。首轮同时插入250条测试回填出现250→150→50→0，健康/SQL均成功，原passed=false结果保留；不把正常短暂测试积压当持续故障或删除失败。此短测不是完整正式资源/任务/备份探针的24小时可靠性证明。

## 数据保全与未解决风险

正式历史84616条目录基线缺失/变更0，正式Fixture0，积压0，既有按块字节哈希检查128547条、失败0，51迁移及历史迁移文件变化0。业务库持续增长，查询时间见[s69-r1-formal-evidence-20261013T111718.json](evidence/s69-r1-formal-evidence-20261013T111718.json)；本轮未在原窗口中重扫全库字节增加负载。

剩余风险：历史连接持有者未知，长事务/锁等待/慢SQL/GC或线程饥饿不能排除；候选未正式部署；短测只覆盖HTTP/SSE/SQL，需要完整正式探针预检；系统上次NTP成功同步12:19:48、根分散7.8261929秒，需维护前复核外部校时。本轮不自动改5173、不重启正式Java/共用PostgreSQL、不新开24小时窗口、不进入S67。

## 实际修改文件

- backend/src/main/java/cn/huankan/event/QueryService.java
- backend/src/main/java/cn/huankan/stream/StreamService.java
- backend/src/main/java/cn/huankan/collector/CollectorService.java
- backend/src/main/java/cn/huankan/collector/CollectorGuard.java
- backend/src/main/java/cn/huankan/reliability/JdbcDiagnostics.java
- backend/src/main/java/cn/huankan/reliability/PoolSnapshotLogger.java
- backend/src/main/resources/application.yml
- backend/src/test/java/cn/huankan/event/BoundedEventQueryTest.java
- backend/src/test/java/cn/huankan/reliability/IsolatedReliabilityDatabase.java
- backend/src/test/java/cn/huankan/reliability/ReliabilityDatabaseTest.java
- backend/src/test/java/cn/huankan/reliability/JdbcDiagnosticsTest.java
- scripts/audit-s69-r1-evidence.mjs
- scripts/diagnose-s69-r1.mjs
- scripts/observe-s69-r1-readiness.mjs
- scripts/prepare-s69-r1-browser-tests.mjs
- scripts/restart-s69-r1-test-jvm.ps1
- scripts/start-s69-r1-test.ps1
- scripts/verify-s69-r1-jvm-lifecycle.mjs
- scripts/write-s69-r1-reports.mjs
- scripts/vite.s69-isolated.template.ts
- scripts/serve-s69-static.mjs
- scripts/prepare-s69-isolation.mjs
- scripts/verify-s69-isolation.mjs
- scripts/start-s69-static-observation.ps1
- scripts/inspect-s69-processes.ps1
- scripts/audit-s69-final.mjs
- scripts/write-s69-isolation-report.mjs

所有原始/脱敏日志、源码版本、候选资源和报告的SHA256以最终索引及交付清单为准，不覆写旧索引。以下完整异常链仅原日志脱敏提取，不补造缺失字段。

### 原异常链1：runtime/s70-main.log，2026-10-13T15:56:01.860+08:00

~~~text
2026-10-13T15:56:01.860+08:00 ERROR 43884 --- [1-18080-exec-11] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection] with root cause

java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30021ms (total=10, active=10, idle=0, waiting=11)
	at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:714) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:184) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:142) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127) ~[HikariCP-6.3.3.jar!/:na]
	at org.springframework.jdbc.datasource.DataSourceUtils.fetchConnection(DataSourceUtils.java:160) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.doGetConnection(DataSourceUtils.java:118) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:81) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:653) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:723) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:754) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:767) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:820) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.queryForList(JdbcTemplate.java:954) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at cn.huankan.event.QueryService.events(QueryService.java:23) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:360) ~[spring-aop-6.2.19.jar!/:6.2.19]
	at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724) ~[spring-aop-6.2.19.jar!/:6.2.19]
	at cn.huankan.event.QueryService$$SpringCGLIB$$0.events(<generated>) ~[!/:0.1.0]
	at cn.huankan.event.ApiController.events(ApiController.java:19) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:991) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:896) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) ~[tomcat-embed-websocket-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$3(FilterChainProxy.java:231) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$FilterObservation$SimpleFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:490) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:351) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator.lambda$wrapSecured$0(ObservationFilterChainDecorator.java:83) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:129) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:125) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:119) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:131) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:85) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:179) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.SecurityConfig$1.doFilterInternal(SecurityConfig.java:37) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.RequestRateLimitFilter.doFilterInternal(RequestRateLimitFilter.java:25) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:107) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:93) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$0(ObservationFilterChainDecorator.java:334) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:225) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:319) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.handler.HandlerMappingIntrospector.lambda$createCacheFilter$4(HandlerMappingIntrospector.java:267) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:240) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:362) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:278) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.ServerHttpObservationFilter.doFilterInternal(ServerHttpObservationFilter.java:110) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:88) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:492) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:113) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:83) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:72) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1272) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1797) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:973) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:491) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]


~~~

### 原异常链2：runtime/s70-main.log，2026-10-13T15:56:02.354+08:00

~~~text
2026-10-13T15:56:02.354+08:00 ERROR 43884 --- [   scheduling-4] o.s.s.s.TaskUtils$LoggingErrorHandler    : Unexpected error occurred in scheduled task

org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection
	at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:84) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:388) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:476) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:486) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.queryForObject(JdbcTemplate.java:519) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.queryForObject(JdbcTemplate.java:526) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at cn.huankan.stream.StreamService.cursor(StreamService.java:37) ~[!/:0.1.0]
	at cn.huankan.stream.StreamService.publish(StreamService.java:64) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.scheduling.support.ScheduledMethodRunnable.runInternal(ScheduledMethodRunnable.java:130) ~[spring-context-6.2.19.jar!/:6.2.19]
	at org.springframework.scheduling.support.ScheduledMethodRunnable.lambda$run$2(ScheduledMethodRunnable.java:124) ~[spring-context-6.2.19.jar!/:6.2.19]
	at io.micrometer.observation.Observation.observe(Observation.java:498) ~[micrometer-observation-1.15.12.jar!/:1.15.12]
	at org.springframework.scheduling.support.ScheduledMethodRunnable.run(ScheduledMethodRunnable.java:124) ~[spring-context-6.2.19.jar!/:6.2.19]
	at org.springframework.scheduling.config.Task$OutcomeTrackingRunnable.run(Task.java:87) ~[spring-context-6.2.19.jar!/:6.2.19]
	at org.springframework.scheduling.support.DelegatingErrorHandlingRunnable.run(DelegatingErrorHandlingRunnable.java:54) ~[spring-context-6.2.19.jar!/:6.2.19]
	at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:572) ~[na:na]
	at java.base/java.util.concurrent.FutureTask.runAndReset(FutureTask.java:358) ~[na:na]
	at java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:305) ~[na:na]
	at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144) ~[na:na]
	at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642) ~[na:na]
	at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]
Caused by: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30005ms (total=10, active=10, idle=0, waiting=10)
	at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:714) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:184) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:142) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127) ~[HikariCP-6.3.3.jar!/:na]
	at org.springframework.jdbc.datasource.DataSourceUtils.fetchConnection(DataSourceUtils.java:160) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.doGetConnection(DataSourceUtils.java:118) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:81) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	... 21 common frames omitted


~~~

### 原异常链3：runtime/s70-main.log，2026-10-13T15:56:02.470+08:00

~~~text
2026-10-13T15:56:02.470+08:00 ERROR 43884 --- [.1-18080-exec-7] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.jdbc.CannotGetJdbcConnectionException: Failed to obtain JDBC Connection] with root cause

java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30007ms (total=10, active=10, idle=0, waiting=9)
	at com.zaxxer.hikari.pool.HikariPool.createTimeoutException(HikariPool.java:714) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:184) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:142) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.HikariDataSource.getConnection(HikariDataSource.java:127) ~[HikariCP-6.3.3.jar!/:na]
	at org.springframework.jdbc.datasource.DataSourceUtils.fetchConnection(DataSourceUtils.java:160) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.doGetConnection(DataSourceUtils.java:118) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.datasource.DataSourceUtils.getConnection(DataSourceUtils.java:81) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:388) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:476) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:486) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.queryForList(JdbcTemplate.java:536) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at cn.huankan.stream.BrowserObservationService.status(BrowserObservationService.java:19) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:360) ~[spring-aop-6.2.19.jar!/:6.2.19]
	at org.springframework.aop.framework.CglibAopProxy$DynamicAdvisedInterceptor.intercept(CglibAopProxy.java:724) ~[spring-aop-6.2.19.jar!/:6.2.19]
	at cn.huankan.stream.BrowserObservationService$$SpringCGLIB$$0.status(<generated>) ~[!/:0.1.0]
	at cn.huankan.stream.BrowserObservationController.status(BrowserObservationController.java:8) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:991) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:896) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) ~[tomcat-embed-websocket-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$3(FilterChainProxy.java:231) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$FilterObservation$SimpleFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:490) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:351) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator.lambda$wrapSecured$0(ObservationFilterChainDecorator.java:83) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:129) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:125) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:119) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:131) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:85) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:179) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.SecurityConfig$1.doFilterInternal(SecurityConfig.java:37) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.RequestRateLimitFilter.doFilterInternal(RequestRateLimitFilter.java:25) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:107) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:93) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$0(ObservationFilterChainDecorator.java:334) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:225) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:319) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.handler.HandlerMappingIntrospector.lambda$createCacheFilter$4(HandlerMappingIntrospector.java:267) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:240) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:362) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:278) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.ServerHttpObservationFilter.doFilterInternal(ServerHttpObservationFilter.java:110) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:88) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:492) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:113) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:83) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:72) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1272) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1797) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:973) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:491) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]


~~~

### 原异常链4：runtime/s65-research.log，2026-10-13T10:16:37.624+08:00

~~~text
2026-10-13T10:16:37.624+08:00 ERROR 47496 --- [.1-18086-exec-3] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception [Request processing failed: org.springframework.jdbc.BadSqlGrammarException: PreparedStatementCallback; bad SQL grammar [SELECT s.*,r.name publisher,r.region,o.observation_date latest_period,o.value::text latest_value FROM macro_series s JOIN source_registry r ON r.id=s.source_id JOIN source_license l ON l.source_id=s.source_id LEFT JOIN LATERAL(SELECT * FROM macro_observation WHERE series_id=s.id ORDER BY observation_date DESC,version DESC LIMIT 1)o ON true WHERE l.status='APPROVED' AND l.display_allowed AND (l.valid_until IS NULL OR l.valid_until>now()) AND (? IS NULL OR s.source_id=?) ORDER BY s.source_id,s.id LIMIT 50 OFFSET ?]] with root cause

org.postgresql.util.PSQLException: 错误: 无法确定参数 $1 的数据类型
	at org.postgresql.core.v3.QueryExecutorImpl.receiveErrorResponse(QueryExecutorImpl.java:2904) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.core.v3.QueryExecutorImpl.processResults(QueryExecutorImpl.java:2589) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.core.v3.QueryExecutorImpl.execute(QueryExecutorImpl.java:431) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.jdbc.PgStatement.executeInternal(PgStatement.java:534) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.jdbc.PgStatement.execute(PgStatement.java:437) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.jdbc.PgPreparedStatement.executeWithFlags(PgPreparedStatement.java:196) ~[postgresql-42.7.11.jar!/:42.7.11]
	at org.postgresql.jdbc.PgPreparedStatement.executeQuery(PgPreparedStatement.java:139) ~[postgresql-42.7.11.jar!/:42.7.11]
	at com.zaxxer.hikari.pool.ProxyPreparedStatement.executeQuery(ProxyPreparedStatement.java:52) ~[HikariCP-6.3.3.jar!/:na]
	at com.zaxxer.hikari.pool.HikariProxyPreparedStatement.executeQuery(HikariProxyPreparedStatement.java) ~[HikariCP-6.3.3.jar!/:na]
	at org.springframework.jdbc.core.JdbcTemplate$1.doInPreparedStatement(JdbcTemplate.java:732) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.execute(JdbcTemplate.java:658) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:723) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:754) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:767) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.query(JdbcTemplate.java:820) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at org.springframework.jdbc.core.JdbcTemplate.queryForList(JdbcTemplate.java:954) ~[spring-jdbc-6.2.19.jar!/:6.2.19]
	at cn.huankan.event.MacroChangeService.catalog(MacroChangeService.java:5) ~[!/:0.1.0]
	at cn.huankan.event.MacroChangeController.catalog(MacroChangeController.java:6) ~[!/:0.1.0]
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103) ~[na:na]
	at java.base/java.lang.reflect.Method.invoke(Method.java:580) ~[na:na]
	at org.springframework.web.method.support.InvocableHandlerMethod.doInvoke(InvocableHandlerMethod.java:258) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.method.support.InvocableHandlerMethod.invokeForRequest(InvocableHandlerMethod.java:191) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod.invokeAndHandle(ServletInvocableHandlerMethod.java:118) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.invokeHandlerMethod(RequestMappingHandlerAdapter.java:991) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.handleInternal(RequestMappingHandlerAdapter.java:896) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter.handle(AbstractHandlerMethodAdapter.java:87) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doDispatch(DispatcherServlet.java:1089) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.DispatcherServlet.doService(DispatcherServlet.java:979) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.processRequest(FrameworkServlet.java:1014) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.FrameworkServlet.doPost(FrameworkServlet.java:914) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:590) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:885) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at jakarta.servlet.http.HttpServlet.service(HttpServlet.java:658) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:193) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.websocket.server.WsFilter.doFilter(WsFilter.java:51) ~[tomcat-embed-websocket-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:108) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.FilterChainProxy.lambda$doFilterInternal$3(FilterChainProxy.java:231) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$FilterObservation$SimpleFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:490) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$1(ObservationFilterChainDecorator.java:351) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator.lambda$wrapSecured$0(ObservationFilterChainDecorator.java:83) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:129) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.intercept.AuthorizationFilter.doFilter(AuthorizationFilter.java:101) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:125) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.access.ExceptionTranslationFilter.doFilter(ExceptionTranslationFilter.java:119) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:131) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.SessionManagementFilter.doFilter(SessionManagementFilter.java:85) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.AnonymousAuthenticationFilter.doFilter(AnonymousAuthenticationFilter.java:100) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.servletapi.SecurityContextHolderAwareRequestFilter.doFilter(SecurityContextHolderAwareRequestFilter.java:179) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.savedrequest.RequestCacheAwareFilter.doFilter(RequestCacheAwareFilter.java:63) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.SecurityConfig$1.doFilterInternal(SecurityConfig.java:37) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at cn.huankan.security.RequestRateLimitFilter.doFilterInternal(RequestRateLimitFilter.java:25) ~[!/:0.1.0]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:107) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.authentication.logout.LogoutFilter.doFilter(LogoutFilter.java:93) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doHeadersAfter(HeaderWriterFilter.java:90) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.header.HeaderWriterFilter.doFilterInternal(HeaderWriterFilter.java:75) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:82) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.SecurityContextHolderFilter.doFilter(SecurityContextHolderFilter.java:69) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter.doFilterInternal(WebAsyncManagerIntegrationFilter.java:62) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:228) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.session.DisableEncodeUrlFilter.doFilterInternal(DisableEncodeUrlFilter.java:42) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.wrapFilter(ObservationFilterChainDecorator.java:241) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$AroundFilterObservation$SimpleAroundFilterObservation.lambda$wrap$0(ObservationFilterChainDecorator.java:334) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$ObservationFilter.doFilter(ObservationFilterChainDecorator.java:225) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.ObservationFilterChainDecorator$VirtualFilterChain.doFilter(ObservationFilterChainDecorator.java:138) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilterInternal(FilterChainProxy.java:233) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.security.web.FilterChainProxy.doFilter(FilterChainProxy.java:191) ~[spring-security-web-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.ServletRequestPathFilter.doFilter(ServletRequestPathFilter.java:52) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebSecurityConfiguration.java:319) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.servlet.handler.HandlerMappingIntrospector.lambda$createCacheFilter$4(HandlerMappingIntrospector.java:267) ~[spring-webmvc-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter$VirtualFilterChain.doFilter(CompositeFilter.java:113) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.CompositeFilter.doFilter(CompositeFilter.java:74) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.security.config.annotation.web.configuration.WebMvcSecurityConfiguration$CompositeFilterChainProxy.doFilter(WebMvcSecurityConfiguration.java:240) ~[spring-security-config-6.5.11.jar!/:6.5.11]
	at org.springframework.web.filter.DelegatingFilterProxy.invokeDelegate(DelegatingFilterProxy.java:362) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.DelegatingFilterProxy.doFilter(DelegatingFilterProxy.java:278) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.RequestContextFilter.doFilterInternal(RequestContextFilter.java:100) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.FormContentFilter.doFilterInternal(FormContentFilter.java:93) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.ServerHttpObservationFilter.doFilterInternal(ServerHttpObservationFilter.java:110) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.springframework.web.filter.CharacterEncodingFilter.doFilterInternal(CharacterEncodingFilter.java:201) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.springframework.web.filter.OncePerRequestFilter.doFilter(OncePerRequestFilter.java:116) ~[spring-web-6.2.19.jar!/:6.2.19]
	at org.apache.catalina.core.ApplicationFilterChain.internalDoFilter(ApplicationFilterChain.java:162) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.ApplicationFilterChain.doFilter(ApplicationFilterChain.java:138) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardWrapperValve.invoke(StandardWrapperValve.java:165) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardContextValve.invoke(StandardContextValve.java:88) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.authenticator.AuthenticatorBase.invoke(AuthenticatorBase.java:492) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardHostValve.invoke(StandardHostValve.java:113) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.valves.ErrorReportValve.invoke(ErrorReportValve.java:83) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.core.StandardEngineValve.invoke(StandardEngineValve.java:72) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.catalina.connector.CoyoteAdapter.service(CoyoteAdapter.java:342) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.http11.Http11Processor.service(Http11Processor.java:399) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProcessorLight.process(AbstractProcessorLight.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.coyote.AbstractProtocol$ConnectionHandler.process(AbstractProtocol.java:1272) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1797) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.net.SocketProcessorBase.run(SocketProcessorBase.java:52) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:973) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:491) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at org.apache.tomcat.util.threads.TaskThread$WrappingRunnable.run(TaskThread.java:63) ~[tomcat-embed-core-10.1.55.jar!/:na]
	at java.base/java.lang.Thread.run(Thread.java:1583) ~[na:na]


~~~


## R1交付终态补充

原双端浏览器末采样19:47:45.962连接且可见；finished_at=19:48:01.017后按原脚本关闭，原43988/36400进程已退出，未继续延长。原s69-live自动化已实际暂停，旧S59暂停任务未改。

窗口结束后单次现场复核见[evidence/s69-r1-final-live-state-20261013T120148.json](evidence/s69-r1-final-live-state-20261013T120148.json)：20:01:48健康HTTP200/UP、SSE状态HTTP200、官方双端LIVE0、积压0；当时原始证据129487、既有字节检查失败0、未覆盖0、迁移51。此为单次复核，不是新的连续窗口或重复全库字节扫描。

仅本轮测试Java18088/静态5184于19:56:45正常结束，测试schema及SQL导出保留，见[evidence/s69-r1-test-cleanup-20261013-195642.json](evidence/s69-r1-test-cleanup-20261013-195642.json)；5173仍PID43740、5180仍49192，独立开发5181为61124、候选5182为57668。原Java43884和共享PostgreSQL未操作。当前实际进程见[evidence/s69-r1-processes-final-20261013-1958.json](evidence/s69-r1-processes-final-20261013-1958.json)，文件名不替代内部实际时间。

补充新增交付脚本：scripts/stop-s69-r1-test.ps1、scripts/final-s69-r1-state.mjs、scripts/preserve-s69-r1-delivery.mjs。实际无人工确认/错误清单SHA拒绝启动保护见[evidence/s69-r1-switch-gates-20261013T1204.json](evidence/s69-r1-switch-gates-20261013T1204.json)，未绑定或替换5173。

原最终索引另有按SHA256命名副本：[s69-r1-original-final-index-sha256-3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14.json](evidence/s69-r1-original-final-index-sha256-3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14.json)。旧哈希不改写；本轮所有代码、日志、测试JSON/XML、截图、测试schema导出及报告冻结副本的交付清单为[s69-r1-delivery-20261013T120843.json](evidence/s69-r1-delivery-20261013T120843.json)。静态切换和新24小时仍未准入，等待实际人工确认与剩余预检。

## 2026-10-13 21:15 S69-R1交付复核

本次先核对原JSON实际finished_at及既有最终索引，确认健康窗口2026-10-13 19:32:59.160、双浏览器窗口19:48:01.017结束（北京时间）。A/B/C失败、D等待真实官方双端LIVE样本（0）保持，不重开、不拼接、不改原哈希。

[冻结与代码复核](evidence/s69-r1-recheck-20261013T211241.json)：原窗口及交付冻结文件共141份SHA-256匹配，30份代码/脚本与交付版本匹配。原索引SHA-256仍为3a042a36ebe9da408e08f697bdca87edb8738e70afcdc53cefe9342b7afe4e14；候选JAR仍为d79735d9dbe8159f70d942f5a8a9928699df16d76ca22fabdc78c7f1c323ca28。保留原索引和全部冻结副本，本次仅新增核验记录。

[静态版本复核](evidence/s69-r1-recheck-static-20261013T2115.json)：候选20261013T105255的5份资源SHA匹配；原frontend147份文件SHA匹配。5182首页HTTP200且版本响应头正确，manifest SHA仍为0023771f6a6b902eb33bde9eb99c622f256b3e6925447834e73cbbf79447e1d5。[进程现场](evidence/s69-r1-recheck-processes-20261013T211241.json)确认Java43884及5173旧Vite43740创建时间未变，5181独立开发61124、5182静态57668保持；其他项目0.0.0.0:5173监听风险仍存在，未操作任何进程。

[21:12:48后单次现场查询](evidence/s69-r1-final-live-state-20261013T131248.json)：健康200/UP、SSE状态接口200、官方双端LIVE0、Outbox审计积压0；原始证据132363、既有完整性检查失败0、未覆盖0、迁移51。此为只读数据库状态和既有检查记录复核，不是重新全库字节扫描或新连续观察。

本次核对既有完整测试日志：后端202项（失败/错误/跳过均0）、前端19项、浏览器18项（异常/跳过/不稳定均0）、隔离检查10项通过；未重复执行故障注入、JVM关闭恢复或浏览器全量测试。正式滚动Java日志仍仅检出原15:56三条获取连接失败，未发现新增同类异常。历史饱和的连接持有者、SQL/事务和主机暂停证据仍缺失；保持“已修复独立复现风险，历史最终根因未闭环”的结论。正式连接池和调度线程池未调整。

5173切换及新24小时仍未准入：候选正式后端未部署，完整采样探针预检与外部校时、5173精确地址冲突处理尚待完成，用户确认尚未取得。本次不请求以确认替代技术门禁；满足剩余门禁后再取得明确切换和启动授权。没有重启正式Java/共享PostgreSQL、修改旧5180源码、写入正式模拟事件、清空Outbox、启动新窗口或进入S67。
