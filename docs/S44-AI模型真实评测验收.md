# S44 AI模型真实评测验收

结论：真实模型配置缺失，真实AI执行0；质量、幻觉率、引文准确率、成本与耗时尚未验收。不能用规则模板充当AI结果。

实际实现：V43；AiEvaluationService/Controller；保留既有AiResearchService真实HTTP调用、失败记录、提示版本、输出校验和预算门禁。基于2份已入库官方宏观证据准备16条评测样本，8类任务为中文摘要、机构、宏观指标、金额单位、原文引述、正负影响、未知字段、非可信内容处理。样本持久化证据ID、哈希、原始预期字段、提示版本；准备样本不增加模型调用次数。

当前样本主要是宏观证据，event_id为空；现有按事件调用入口不能直接运行这批宏观样本。这是明确剩余接口工作，配置模型后仍须补齐宏观样本执行/评分与真实事件样本，不能宣称评测执行器已全面验收。没有构造攻击样本混入金融业务库；真实提示注入防护质量须用隔离评测输入测量。

不自动反复执行付费请求；失败原样保留，再次显式发起仍须预算通过。本地环境使用AI_ENABLED、AI_BASE_URL、AI_MODEL、AI_API_KEY、AI_MAX_REQUESTS_PER_DAY，默认禁用/预算0；不要把密钥贴入报告或仓库。页面显示未配置和真实执行0。[配置与样本状态](evidence/s46-acceptance-details.json)。

## 实际环境与验证记录

记录时间：2026-10-10T16:25:21.571025+08:00。数据库计数对应证据快照 2026-10-10T16:20:41.700969+08:00，后台采集仍在运行，不将快照写成永久固定数。

正式库：127.0.0.1:5432/huankan；独立测试实例：55432/huankan_test。凭证从本地环境变量读取，不写进本报告。正式 Flyway 当前 45 次迁移；V1—V39 校验变化 0。

本轮实际源文件清单及SHA-256：[s46-modified-files.json](evidence/s46-modified-files.json)。原始基线：[s39-preserved-ledgers.json](evidence/s39-preserved-ledgers.json)。最终计数：[s46-final-state.json](evidence/s46-final-state.json)。

回归：后端 121项通过、0失败、0错误、0跳过；前端5项单元测试通过且生产构建成功；正式浏览器 10项通过；隔离双浏览器推送 1项通过。命令：加载 scripts/load-local-env.ps1 后，用Java21执行 `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package`；在frontend执行 `npm.cmd test`、`npm.cmd run build`、`npx.cmd playwright test`；隔离浏览器使用 `scripts/start-isolated-web.ps1`、`npx.cmd playwright test -c playwright-isolated.config.ts`、`scripts/stop-isolated-web.ps1`。日志均保留在runtime/s46-*，最终后端日志为s46-backend-tests-v45.log。
