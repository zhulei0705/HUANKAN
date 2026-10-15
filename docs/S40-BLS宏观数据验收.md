# S40 BLS宏观数据验收

结论：接入代码、隔离数据库验证与许可登记完成；正式API连通验收未通过。2026-10-10实际请求3个已核实序列，年份2025—2026，源站HTTP403，任务FAILED，入库0、重复0，无金融业务记录。403响应原文、SHA-256、请求体、时间、错误保存在bls_request，哈希错误0；本地管理接口返回200并不代表源站成功。失败后没有以网页数据替代，也没有自动重复请求。[真实请求](evidence/s40-bls-real-request.json)。

实际实现：V41；BlsCollector、BlsSourceAdapter、BlsController及6项解析测试。V1不需要Key；持久化预约与专用锁防止跨重启超额，保守滚动24小时25次、每次25个序列、10个年份。429/官方请求拒绝触发冷却；HTTP200仍必须检查REQUEST_SUCCEEDED、系列匹配与严格JSON。M13年均值不冒充月份，缺失值不改为0。

已核实配置：CUUR0000SA0为未季调CPI指数，1982—84=100；LNS14000000为季调失业率百分比；CES0000000001为季调非农就业总人数、千人，不是新增就业人数。CUSR0000SA0L1E与WPUFD4保留真实代码，但单位/完整定义未核实，禁用。单位和季调口径不互换。

保存原始观测、月份、原始值、脚注及初值/修订说明。API没有可验证的首次公告时间，published_at保持空值，观测日期仅表达月份。首次导入历史回填，不重大提醒。隔离测试验证同值重采不增加事件、脚注/值变化保留新旧版本、事务回滚及API业务失败门禁；这些结果不是BLS正式入库通过。

许可依据：[BLS官方条款](https://www.bls.gov/developers/termsOfService.htm)、[官方FAQ](https://www.bls.gov/developers/api_faqs.htm)、[官方序列定义](https://data.bls.gov/timeseries/CUUR0000SA0)。已登记检查时间、统计字段使用范围，页面标明获取日期、BLS来源和二次分析不由BLS保证，未使用BLS徽标。[许可记录](evidence/s40-license-review.json)。后续仅在官方访问恢复且预算许可时重试；本轮不能宣称BLS已真实入库。

## 实际环境与验证记录

记录时间：2026-10-10T16:25:21.571025+08:00。数据库计数对应证据快照 2026-10-10T16:20:41.700969+08:00，后台采集仍在运行，不将快照写成永久固定数。

正式库：127.0.0.1:5432/huankan；独立测试实例：55432/huankan_test。凭证从本地环境变量读取，不写进本报告。正式 Flyway 当前 45 次迁移；V1—V39 校验变化 0。

本轮实际源文件清单及SHA-256：[s46-modified-files.json](evidence/s46-modified-files.json)。原始基线：[s39-preserved-ledgers.json](evidence/s39-preserved-ledgers.json)。最终计数：[s46-final-state.json](evidence/s46-final-state.json)。

回归：后端 121项通过、0失败、0错误、0跳过；前端5项单元测试通过且生产构建成功；正式浏览器 10项通过；隔离双浏览器推送 1项通过。命令：加载 scripts/load-local-env.ps1 后，用Java21执行 `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package`；在frontend执行 `npm.cmd test`、`npm.cmd run build`、`npx.cmd playwright test`；隔离浏览器使用 `scripts/start-isolated-web.ps1`、`npx.cmd playwright test -c playwright-isolated.config.ts`、`scripts/stop-isolated-web.ps1`。日志均保留在runtime/s46-*，最终后端日志为s46-backend-tests-v45.log。
