# S49 SEC披露数据工程验收

结论：前置工程已扩展，SEC正式披露采集仍待配置。实际联系邮箱为空、自动采集关闭，公司映射与关注名单不得填入来源不明的CIK；本轮没有发起SEC真实披露请求，正式XBRL表0条。

沿用真实官方公司映射搜索、CIK核验、个人关注公司、配置说明及SEC统一限流器。新增8-K、10-Q、10-K及更正表单定义；XBRL模型保留CIK、taxonomy、concept、单位、会计期间、accession、filed、财年、期间、frame、证据与版本，不混淆瞬时资产和期间流量。解析器严格校验CIK、单位、提交类型、accession与会计期间；报表比较必须同企业、同概念、同单位、同期间类型，不能把不同口径数字当作增长。

新增中文“SEC配置准备”页面和POST启用预检。原联系邮箱及真实UA须由用户在本地填写，不填虚构值、不索要密码或API Key。新数值模型与解析的测试仅在隔离环境，不证明真实SEC文档与XBRL数据闭环；真实公司事实接口证据持久化、披露到财务事实的联动仍须在有效配置后联调。

官方依据：[SEC公平访问与EDGAR数据说明](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)、[官方JSON接口](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)。2026-10-10核查官方当前上限10次/秒；本项目复用跨data.sec.gov/www.sec.gov统一预算，保守2次/秒和120次/分钟，既有403/429/5xx处理继续保留。

## 真实验收快照

生成时间：2026-10-10T09:59:16.669640+00:00。正式库统计时间：2026-10-10T17:58:36.829248+08:00。采集仍在运行，后续计数会变化。

|项目|实际结果|
|---|---|
|数据库|本机 PostgreSQL 5432 / huankan，已应用48个Flyway迁移；测试使用55432 / huankan_test独立schema|
|原始证据 / 金融事件 / Outbox|43194 / 43188 / 43188|
|原子事实|1315，字段核对263，来源核查1052，独立支持0|
|正式Fixture / Outbox重复 / 来源绑定错误|0 / 0 / 0|
|隔离 / 已核实企业关系 / AI实际执行|51 / 0 / 0|
|本轮回归|后端134通过、0失败、0错误、0跳过；既有前端5通过；正式浏览器原10+新增3通过；隔离SSE 1通过|
|新版本备份恢复|48迁移，42993证据，42987Outbox；全部恢复哈希和引用错误0|
|官方日历|FED 76条，ECB 55条，合计131条；计划日期不等于已经发布|
|正式BLS / SEC数值|BLS文件观测0；SEC XBRL 0|
|持续运行|已有窗口起点2026-10-10T08:20:40.693Z，截至快照5911秒，0条中断记录；不足24小时，待验收|
|真实官方LIVE双浏览器样本|0，尚未最终通过|

## 测试命令与证据

在项目根目录加载 `scripts/load-local-env.ps1`，使用 Java 21。

- `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml -B '-Dhuankan.jar.name=huankan-s54-0.1.0' package`：134项测试通过，但旧增量JAR正在运行，Windows重打包重命名失败；完整日志 `runtime/s54-all-backend.log`。
- `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml -B '-Dhuankan.jar.name=huankan-s54-final-0.1.0' '-DskipTests' package`：采用独立新文件名完成打包，BUILD SUCCESS；日志 `runtime/s54-package-final.log`。未修改主服务JAR。
- 前端目录 `npm.cmd test`：5通过；`npx.cmd vue-tsc --noEmit` 与 `npx.cmd vite build --config vite.s54.config.ts`：通过。
- 前端目录 `node.exe node_modules/@playwright/test/cli.js test --config playwright.s54.config.ts`：既有10项及新增3项分批运行，分别记录于 `runtime/s54-browser-regression.log` 与 `runtime/s54-browser-practical-final.log`。
- `scripts/start-isolated-s54-web.ps1` → 前端目录 `node.exe node_modules/@playwright/test/cli.js test --config playwright-s54-isolated.config.ts` → `scripts/stop-isolated-s54-web.ps1`：隔离SSE通过；测试schema已清理，证据 `runtime/s54-isolated-sse-accepted.log`。
- `node scripts/s54-practical-acceptance.mjs diagnose` / `calendars` / `business`：真实官方HTTP、入库与简报API核验，JSON见 `docs/evidence/s54-*.json`。
- `node scripts/audit-s54-ledgers.mjs`：阶段历史基线及45个既有迁移校验码保持；`scripts/backup-restore-s54.ps1`：全库一致备份恢复通过。

## 修改文件与运行入口

完整本阶段源码清单见 `docs/evidence/s54-modified-files.json`。报告使用的证据均留存在 `docs/evidence`，此前S46及更早证据未删除。

新增中文研究页面：http://127.0.0.1:5176/s54.html 。原实时情报主站仍为 http://127.0.0.1:5173/ 。增量只在18083提供研究API，采集开关关闭；主采集、原SSE继续由18080提供。

主服务PID与创建时间、原JAR SHA-256在部署前后保持相同，见 `s54-primary-preserved.json`。新JAR不能被写成已经替换主采集服务。后续正式升级必须使用包含V46–V48的当前源码或新JAR；旧迁移包不能作为新数据库的重启包。需在连续观察完成后按维护流程升级，不能拼接维护前后窗口。

## 尚未完成

BLS真实API与文件仍403；SEC真实邮箱、公司关注与正式采集未启用；真实官方新事件双浏览器接收/渲染样本为0；连续24小时未完成；源端完整历史vintage未证实；无合法市场行情、真实市场一致预期或AI执行；AI/半导体专题无匹配资料时显示暂无真实数据。不能将准备代码、历史回放或隔离测试写成这些目标已通过。
