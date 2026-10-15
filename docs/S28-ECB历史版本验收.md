# S28 ECB历史版本验收

结论：真实历史/增量请求、证据留存和本地回看完成；未取得真实历史修订/vintage验收。

V24/V25、SdmxHistory、MacroHistoryService/Controller与MacroHistory.vue使用官方白名单SDMX接口。支持startPeriod/endPeriod/includeHistory/updatedAfter，日期范围最多730天，响应有界，DTD/外部实体禁用，来源、许可、序列和采集锁校验沿用原框架。

[ECB官方数据API说明](https://data.ecb.europa.eu/help/api/data)已实查。7系列真实查询200，4个汇率各29观测、3个政策利率各1观测，共119个本地版本；原263观测与证据保留。总实际请求 11 次，HTTP200 9，失败 2，解析观测累计 150（含重抓）。

美元重抓29个观测新增0版本。updatedAfter=2026-10-08T00:00:00Z实际200/2观测、新增0；2026-10-09T14:00:00Z实际404，保持失败，不把它当“无变更成功”。V25修复后，HTTP失败保存实际状态、响应字节与SHA，未生成业务事件。最初那次404是在修复前执行，历史请求行缺少HTTP状态/原始失败字节/哈希，依据HTTP观测和HTTP_404错误确认失败；没有追补或伪造当时响应。后续同参数重试的404已具备实际失败响应与哈希。[s28-real-history-requests.json](evidence/s28-real-history-requests.json)、[s28-updated-after-recheck.json](evidence/s28-updated-after-recheck.json)。

实际历史修订 0。Prepared/validFromDate出现响应生成时点，不能当最初发布时间；Last-Modified仅为来源更新时间头，source_vintage_at保持未知。页面“暂无可验证修订记录”，版本比较按本地取得时间回看，as-of拒绝未来时点；不能推断采集前市场可知。隔离验证A→B→A保留三版本、重复不增版本、明确Delete动作和历史不可变；真实官方删除/撤回未遇到，不宣称通过。

验收快照：2026-10-10T10:34:34.035339+08:00。全量实际字节审计快照开始时间：2026-10-10T09:54:39.81883+08:00。后台仍可能采集新批次，以下不是固定实时行情。

正式库：127.0.0.1:5432/huankan，专用应用账号；凭证仅本地环境变量。隔离库：127.0.0.1:55432/huankan_test。Flyway V1—V29 已应用，旧迁移与原始证据保持不变；未重启共享 PostgreSQL，未删除其他项目数据。

| 原始来源 | 正式业务表记录（宏观观测单独解释） |
|---|---:|
| ECB | 29 |
| ECB_DATA | 263 |
| FED | 21 |
| FRED | 0 |
| GDELT | 35706 |
| NBS | 1000 |
| SEC | 0 |

正式原始证据 37019 份、历史 Outbox 37019 条。原始宏观观测 263 个；ECB 历史查询另有 119 个本地版本，不能重复计为新增新闻。原始证据／实际完整响应哈希错误 0/0，Fixture 0，重复业务事件 0，断引用 0，来源与任务绑定错误 0，未来发布时间 0，GDELT越级核实 0。

基线 s15-baseline.json：逐条核对 5703 份证据及 5703 个事件，缺失或哈希改变 0，事件缺失 0。

基线 s23-baseline.json：逐条核对 34252 份证据及 34252 个事件，缺失或哈希改变 0，事件缺失 0。

完整字节审计覆盖当时 36572 份证据；最新分批实际哈希覆盖 37019 份、错误 0，最新Fixture/来源绑定/未来发布时间分别 0/0/0。机器依据：[s30-truth-audit.json](evidence/s30-truth-audit.json)、[s30-latest-state.json](evidence/s30-latest-state.json)、[s30-modified-files.json](evidence/s30-modified-files.json)。

实际测试命令与结果：

- Java 21：加载本地环境变量后，`mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package`，104 项通过、0失败、0错误、0跳过。原生 PostgreSQL、Flyway 重复迁移、事务回滚、幂等、证据约束与新增模块验证均在独立 UUID schema。[s30-backend-tests.log](evidence/s30-backend-tests.log)。
- 前端：在 frontend 执行 `npm.cmd test`、`npm.cmd run build`，5 项通过，类型检查与构建成功。[s30-frontend-tests.log](evidence/s30-frontend-tests.log)。
- 正式浏览器：`npx.cmd playwright test`，8 项通过，含桌面与390px手机、真实来源、宏观图、事件治理、空关系、未配置AI及历史时间语义。[s30-formal-browser-tests.log](evidence/s30-formal-browser-tests.log)。
- 隔离 Web：先启动 `scripts/start-isolated-web.ps1`，再运行 `npx.cmd playwright test -c playwright-isolated.config.ts`，1项通过；随后按已登记PID/创建时间停止并清理专用 schema。[s23-render-isolated-test.log](evidence/s23-render-isolated-test.log)。

保留失败证据：初次 Windows 套接字拒绝、隔离测试计数解析／配置文件名错误、测试环境变量路径错误、AI触发器列名歧义、API共享限流、备份并行I/O期间的治理请求超时。均不计为通过，修复或调整运行顺序后的最终结果另存；未用删除失败日志的方法改变结论。

当前剩余事项：

- 没有当前观测窗口的新官方事件双浏览器 LIVE 接收与渲染样本；S23仍待真实在线样本，不能拿旧事件回放或隔离数据代替。
- AI真实模型、SEC有效联系方式／关注配置、FRED有效凭证／逐序列许可未就绪，正式执行或接入数量保持0。
- 已核实法律实体资料、证券与企业业务关系仍不足，未将机构名称候选变成已核实公司。
- 未取得真实ECB历史修订、撤回与可证实的原始vintage时点；本地版本和响应生成时间不等于完整源站历史。
- 未做24小时或更长连续稳定性验收；实际观测跨度包含开发维护中断，不计算虚假的连续可用率、P95或长期保证。
- 分类仍为可解释规则与原文候选组；跨媒体同事实、后续进展和复杂市场机制须结合真实证据人工审核，不能把URL组数当独立金融事实数。
