# S27 AI金融智能研判验收

结论：兼容模型接口、审计与证据约束流程完成；真实模型能力未验收。

V22/V27/V29、AiResearchService/Controller、AiEvidenceValidator、AiAnalysis.vue、.env.example与本地环境加载脚本增量完成。当前正式模型执行 0 次，模型/端点未配置。见[本地配置说明](AI本地配置与证据约束.md)。外部HTTPS或本机兼容Chat接口可配置；无配置时规则分析继续。

分析记录保留analysis_id、generated_by=AI、模型、提示版本、实际开始/生成时间、输入证据ID、输入哈希、实际摘录及Unicode定位、提取事实、影响假设、不确定性、反证、逐字引用和数值校验。完成记录不可改写。模型产物始终待审核，不自动进入事实或商业关系。

新闻属于不可信输入，系统约束独立，不提供工具/SQL/命令执行。RSS按实际消息片段取证，范围未知拒绝分析。摘要数字、数值、单位、币种、日期、预测/同比/环比/修订口径缺少来源时标记需审核；不是人工语义核实。

隔离兼容HTTP服务验证：200返回的异常收益摘要被标记REVIEW_REQUIRED；503产生FAILED审计且原事件不丢；不存在工具权限或自动事实写入。纯验证器测试包含数字相似误匹配、错币种/日期/比较口径、工具输出与RSS混篇。真实模型翻译、数值提取准确率、事件机制研究和反证质量未执行，不能凭隔离服务宣称通过。

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
