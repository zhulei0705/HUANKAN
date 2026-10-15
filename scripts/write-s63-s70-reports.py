from pathlib import Path
import json, re

root=Path(__file__).resolve().parents[1]
def read(name):
    p=root/'docs/evidence'/name
    return json.loads(p.read_text(encoding='utf-8-sig')) if p.exists() else {}
baseline=read('s63-database-baseline.json');audit=read('s70-formal-audit.json');counts=audit.get('counts',{})
continuity=read('s69-current-continuity-process.json');online=read('s69-current-online-process.json')
runtime=read('s63-runtime-baseline.json');restore=read('s63-backup-restore.json');upgrade=read('s70-restored-upgrade.json')
workflow=read('s70-regression-s70-real-workflow.json')
def log(name):
    p=root/'runtime'/name
    return p.read_text(encoding='utf-8',errors='replace') if p.exists() else ''
def browser_result(name):
    text=log(name)
    passed=re.findall(r'(\d+) passed',text);failed=re.findall(r'(\d+) failed',text)
    return f"通过{passed[-1] if passed else '未结束'}，失败{failed[-1] if failed else '0或尚未结束'}"
verification=f"""
## 数据库与验证记录

新增迁移V49研究排序快照、V50事件关注/个人工作审计、V51持久化关注更新/已读。正式库已应用{counts.get('migrations','待核对')}个迁移；此前48个迁移文件和数据库校验码的比较结果见evidence/s70-formal-audit.json。本轮没有改写V1—V48，没有重建正式数据库或重启5432 PostgreSQL服务。

实际命令：在根目录加载`scripts/load-local-env.ps1`，使用Java21。

- `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml -B '-Dhuankan.jar.name=huankan-s70-final-0.1.0' package`：146项通过，0失败/错误/跳过，runtime/s70-backend-final.log。
- 快照规范化哈希与简报筛选改动后，12项排序/宏观/关注复测及3项简报复测通过；增加复算接口并调整有界候选后，最终`-Dtest=PriorityRulesTest,MacroComparisonTest,DatabaseGateTest#prioritySnapshotsAreImmutablePrivateAndKeepEvidenceIdentity+eventFollowAndWorkActionsNeverVerifyFacts`打包：12项通过，runtime/s70-reviewed-backend.log。测试集合有重复，不能相加为新增独立用例数。
- 前端`npm.cmd test`：19项通过，runtime/s70-frontend-delivery.log；`npm.cmd run build`包含TypeScript检查；`npx.cmd vite build --config vite.s54.config.ts`：主站与兼容入口构建通过，s70-build-delivery.log、s70-compatibility-delivery.log。
- 前端`node.exe node_modules/@playwright/test/cli.js test --config playwright.s70.config.ts`：34个不同用例首轮{browser_result('s70-browser-regression.log')}。修复旧定位器及必要用例后独立复测：{browser_result('s70-browser-repaired.log')}。保留首次失败，不宣称一次全量全绿；最终覆盖以两个日志逐用例核对。
- `start-isolated-s70-web.ps1` → `playwright-s70-isolated.config.ts` → `stop-isolated-s70-web.ps1`：隔离SSE1项通过，s70-isolated-sse-retry.log，测试schema已清理。首轮测试因复制脚本的隔离测试令牌名称不一致失败，日志保留；没有触及正式金融数据。
- `node --test scripts/acceptance-quality.test.mjs`：3项通过，s70-observation-audit.log。
- `scripts/backup-restore-s63.ps1`：正式库一致备份恢复通过，冻结84616条证据、84610条Outbox、48迁移；全部原始/官方响应/压缩包哈希及断引用错误0。备份{restore.get('dump_bytes')}字节，SHA256见evidence/s63-backup-restore.json。`verify-s70-restored-upgrade.ps1`将真实恢复副本升级到51迁移，原清单哈希保持，证据s70-restored-upgrade.json。该恢复范围不包含备份时间之后新生成的研究记录。
- `node scripts/audit-s70.mjs`：真实数据库审计，结果时间{audit.get('queried_at','待生成')}；原始84616条基线逐ID/来源/URL/内容哈希保全比较，无缺失或变更时才通过。

## 外部条件与业务边界

50条外文官方内容仍无可信全文译文；AI接口现场确认未配置、实际执行0。BLS最近实际访问记录为403，不声称本轮恢复，不越过诊断重试预算。SEC/FRED接口现场仍待配置且关闭。没有合法行情与一致预期，不生成价格、收益、资金流或“超预期”。本地历史版本不冒充官方完整vintage。正式企业关系为0时展示证据不足。

研究排序与工作状态不改变事实核验，不提高通知资格。个人研究通知、历史回放、隔离SSE均不能计入正式官方LIVE。现有本机管理身份由同一访问密钥派生，不能宣称已交付独立多用户权限体系。
"""
stages=[
('S63-研发基线复核.md','S63 研发基线与上阶段验收复核',f"""现场基线已复核。初次检查只有5432在监听，旧主采集/前端已停止；未把S56—S62全部标为业务完成。正式库快照时间{baseline.get('queried_at')}，48迁移、84616证据、84610事件/Outbox、1315原子事实、52简报、关注规则0、已核实关系0、待审关系2，字节哈希错误0。

|上阶段任务|实际结论|
|---|---|
|全站中文|已有19路由、三尺寸实测；外文原文与待译边界保留|
|50条待译|清单已处理，可信全文翻译尚未取得，不能称翻译完成|
|详情证据入口|已交付本机只读投影与统一研究详情|
|每日简报|规则快照已有52份；按需生成，不把历史快照称当日新消息或每日自动调度|
|关注持久化|已有数据库能力，但基线实际关注0条；已读原先仅浏览器本机|
|真实官方LIVE|最新中断审计与现场接口均为0个合格双客户端样本|
|连续24小时|复算原单一窗口1412采样通过；后续v3窗口仅约6小时42分后中断，不拼接|
|BLS、SEC、FRED、AI|访问/配置缺口保留|

历史75607条基线逐ID核对无缺失或变更；金融原始证据保持。此前被覆盖的1张S54隔离截图仍是历史资料缺口。本轮复制浏览器套件到e2e-s70，以新输出路径保护旧截图。

修复服务停机阻断前先完成真实全库备份恢复，再启动s70主采集18080、研究18083和前端5173/5176。主服务新PID43884、新独立运行窗口，不继承旧窗口连续性。S64依赖官方证据/许可、S65依赖字段一致观测；S66公司路径受真实关系缺失限制；S67/S68规则功能可独立运行；S69必须等待新窗口与真实事件。

变更文件：capture-s63-baseline.mjs、backup-restore-s63.ps1、start-s70-services.ps1、verify-s70-restored-upgrade.ps1。证据：s63-database-baseline.json、s63-evidence-identity.json、s63-historical-preservation.json、s63-runtime-baseline.json。"""),
('S64-全球事件智能筛选验收.md','S64 全球重要金融事件筛选',"""实现独立Java排序规则、可保存的评分快照、分页重放与冻结输入复算接口。Vue全球重要事件页复用原导航，支持官方优先、日期、地区、事件/产业类别、事实状态、最低优先级、个人关注和原始来源。逐项展示“为什么排在这里”、规则版本与证据哈希。

评分涵盖来源等级、官方发布新近程度、完整证据/字段核对、发布机构、类别、核心宏观、关注、实际同口径变化、已核实关系、证据修订。未知发布时间不借采集时间加分，标题数字和媒体提及数量不加分。十二类只分类真实已有事件；不制造缺失类别的数据。

按来源/原始文档聚类，保留版本；聚合线索独立且不提升为官方事实。最多5000个候选，预选保留官方消息并按时间有界选择；结果再次按评分及稳定ID排序，超过边界明确提示，不宣称全库完整排序。所有评分输入、关注范围、asOf和规则保存，SHA256使用PostgreSQL规范jsonb文本，可从已存输入重新计算。

API：POST /api/practical/priority/rank、snapshot、reproduce。新增V49。变更：PriorityRules.java、PriorityService.java、PriorityController.java、ImportantEvents.vue、App.vue。测试：PriorityRulesTest、DatabaseGateTest新增快照不可变/身份隔离/哈希复核；s63-s70.spec.ts真实评分解释、重放分页、空产业及三尺寸。已核实关系为0时对应评分为0，未据媒体关系补造输入。"""),
('S65-全球宏观变化分析验收.md','S65 全球官方宏观变化分析',"""复用真实macro_observation、原子事实及research_input_snapshot，规则升级到宏观同口径比较-1.1。MacroComparison检查指标、单位、频率、倍率、币种/报价分母及已提供的季调、基期、统计口径、缺失与变更标记；不一致时停止计算。

保存当前、前期、上年同期的实际证据与版本，显示绝对差值、相对变化率、满足连续月份时的环比和有真实同期输入时的同比。零分母与缺失值不计算比例，日历不连续或政策变更日不冒充连续环比。季调字段未提供时明确未知，数学变化不冒充官方季调新闻增长率；未保存到输入字段的官方口径变化仍需进一步核查。

前端展示公式、单位、比较状态和冻结输入，保留原历史曲线与本地修订边界。真实ECB工作流样本见evidence/s70-regression-s70-real-workflow.json；BLS403时没有生成新的BLS观测，FRED未配置仍关闭。

变更：MacroComparison.java、MacroResearchService.java、PracticalIntelligenceService.java、PracticalDesk.vue；无独立新增数值表，复用原快照表。测试：MacroComparisonTest的单位/频率/季调/倍率差异、月份缺口、真实同期、零/缺失；已有PostgreSQL BLS隔离数值、修订及截止测试保留。原因仅给出规则研究假设，不声称已证实因果。"""),
('S66-金融影响传导研究验收.md','S66 全球金融影响传导研究',"""复用impact_assessment与ImpactService，不新增预测或交易模块。规则升级到规则研判-1.1，保存真实事件ID、事实ID、原始证据、输入哈希、事实状态、截止时间、机制假设、正反解释、缺口、观察变量与市场验证状态。

合格官方事实和完整/获准证据才进入规则分析。实体绑定与关系只取已核实记录；关系输入检查许可、原始哈希、披露时间、有效期和截止前审核。区分法人、并表、行业与供应关系，不能把GLEIF会计合并用作客户供应证明。详情的字段核对事实可发起实际规则研究，并回到原始支持证据。

当前正式已核实关系0，因此实体/行业/企业传导路径可能为空，明确证据不足。没有生成正式供应商、收入敞口、敏感度数值、行情、收益或资金流；市场结果仍未验证。宏观机制说明属于规则假设，不是官方确定解释或AI真实执行。

变更：ImpactService.java、EventResearchDetail.vue；复用既有POST /api/impact/analyze、不可变impact_assessment及关系证据门禁。既有事实截止、原始哈希、研判不可变和企业关系审查回归通过；新增真实ECB详情操作的规则版本与事实证据链在S70浏览器验证。尚无可靠披露的企业路径不能作为业务通过样本。"""),
('S67-事件生命周期验收.md','S67 事件生命周期与关注跟踪',f"""用户可对真实特定事件持久化关注、取消关注，刷新后读取数据库状态；沿用央行、国家、指标、法人公司、产业等已有真实目录。EVENT扩展复用watch_matches，因此不绕开来源、个人最低优先级或通知资格。

生命周期时间线来自真实发现、证据版本、原子事实/审核、规则研究及个人工作动作；工作动作仅研究中/个人归档，要求至少10字实际理由并不可变保存。事实核验、修订和冲突来自事实记录，阅读或开始研究不能改变它们。

新增持久化站内关注更新、通知原因与已读表，官方更新要求关注创建之后的合格官方证据、非历史回填、许可和原始哈希。个人研究工作状态单独通知；前端30秒轮询，离线后查询持久记录补读。实时SSE沿用原资格/Outbox流程，本站提醒轮询不称实时SSE发送证明。

真实工作流：{workflow.get('event_id','待实际浏览器生成')}，证据{workflow.get('evidence_id','待生成')}。临时关注用于实际官方观测核查后取消，个人研究/已读审计记录保留；不是伪造官方新事件，不能计入官方LIVE。

变更：V50、V51、WatchService.java、EventLifecycleService/Controller.java、WatchUpdateService/Controller.java、EventLifecycle.vue、WatchUpdates.vue、App.vue、EventResearchDetail.vue。测试：EVENT匹配/身份隔离、工作动作不产生事实审核、不可变记录、历史回填不生成官方更新、非所有者不能标已读；浏览器覆盖真实关注刷新、个人工作通知与已读。真实官方新版本通知样本仍等待合格事件，关系/冲突的真实后续样本不足。"""),
('S68-每日金融简报质量验收.md','S68 每日全球金融简报质量升级',"""规则简报升级到金融证据规则简报-1.1，按上海时区日期、实际查询截止、来源/专题/个人关注冻结。官方条目从日期范围内最多2000个候选按可解释研究规则选前20；明确有界范围，不把旧发布时间或重复消息数量当今日重大事件。

简报保存覆盖与快照、重要官方信息、宏观观测、产业条目、重点研究、影响假设、反面解释、风险与待核线索、数据源缺口、原始证据目录。产业分区仅使用实际企业/技术/能源/产业链分类；无匹配行业为空。保存生成输入及规范jsonb哈希，旧简报不可变，不改写旧规则版本。

今天、昨天、自定义日期、关注主题、原文、复制、事件详情与历史版本可用；切换范围时清除旧选中简报，避免把不同日期旧内容误作当前范围。事实与GDELT线索分区、分别计数，没有模型时明示规则生成。

变更：PracticalIntelligenceService.java、PracticalDesk.vue，复用financial_brief，不新增模拟新闻。三项真实PostgreSQL简报筛选/历史/关注输入测试及浏览器简报/复制/详情回链通过记录见日志。已有每日日期查询与手工生成；未新增每天自动生成定时任务，不把按需运行称全天候自动日刊。"""),
('S69-运行稳定性与LIVE验收.md','S69 持续运行与真实在线推送门禁',f"""原连续日志重新执行审计：{runtime.get('prior_continuity',{}).get('status','待核查')}，1412采样，独立2026-10-10 16:20—11日16:20窗口；来源失败任务单独保留。后续S59 v3在主机重启后中断，仅约6小时42分，无合格官方LIVE样本，原始JSON/JSONL未覆盖，不拼接。

新的应用健康窗口：{continuity.get('file','尚未启动')}，进程元数据s69-current-continuity-process.json。双独立浏览器窗口：{online.get('file','等待页面验收结束后启动')}，元数据s69-current-online-process.json。二者有各自起止，不把结束前样本称24小时完成。新主采集PID43884保持，不为构建或普通验收重启。

observe-s69-continuity记录进程身份、CPU/内存、磁盘剩余、数据库连接/大小、采集任务、HTTP错误与限流、调度锁、Outbox审计积压、SSE及备份状态；observe-s69-online记录两端连接、可见渲染列表、接收ACK与请求失败。首次健康观察脚本因旧引用重命名错误未形成采样，已保留s69-continuity-startup-failure.json并修正，新窗口独立开始，不计前次尝试。

现场stream/browser/status仍以真实结果为准；个人通知、回放、隔离测试不计正式LIVE。当前报告不作新24小时或官方双端LIVE通过结论。若采样失败或主机再重启，需要审计中断并建立独立窗口。

当前窗口进程身份与最新采样现场核查见s69-current-window-audit.json。已配置本聊天每小时静默后续验收s69-live，仅在有意义变化、失败或完成时通知，旧暂停任务保持暂停。\n\n变更：start-s70-services.ps1、observe-s69-continuity.mjs、observe-s69-online.mjs。三个观察审计测试及隔离SSE通过；正式LIVE等待真实官方发布发生时两个客户端已在线并完整接收、渲染、ACK。"""),
('S70-产品级综合验收.md','S70 产品级综合验收',f"""交付本机可运行的Java/Vue研究流程：重要事件排序 → 原始事实与证据 → 宏观变化 → 条件影响研究 → 持久化关注/个人工作状态 → 简报与历史版本。技术验证与真实业务样本分层，不将代码、历史回放或个人验收工作动作等同官方市场结果。

数据库审计时间：{audit.get('queried_at','待生成')}。实际数量：

|项目|数量|
|---|---|
|原始证据 / 事件 / Outbox|{counts.get('evidence')} / {counts.get('events')} / {counts.get('outbox')}|
|官方事件 / 聚合线索|{counts.get('official_events')} / {counts.get('aggregated_leads')}|
|原子事实|{counts.get('facts')}|
|已核实 / 待审核关系|{counts.get('verified_relations')} / {counts.get('pending_relations')}|
|AI实际执行 / 正式Fixture|{counts.get('ai_executions')} / {counts.get('formal_fixture')}|
|哈希错误 / 事件断引用 / 重复Outbox|{counts.get('hash_errors')} / {counts.get('broken_event_reference')} / {counts.get('duplicate_outbox')}|
|GDELT原子事实支持引用|{counts.get('gdelt_verified_facts')}|
|研究排序快照 / 简报|{counts.get('priority_snapshots')} / {counts.get('briefs')}|

历史证据ID、来源、URL和内容哈希保全，迁移校验与文件保全见s70-formal-audit.json；数量不会用于推断已核实经济影响。原子事实字段核对/来源核查和独立支持应分别查看，独立支持仍无真实样本。没有合法行情表或模型配置时页面保持待验证，不以数值补造市场反应。

浏览器保留原30用例并增加4个，覆盖19路由×三尺寸、字号、对比、横向溢出、筛选/分页、异常与断线恢复、证据和新流程。新重要事件截图：s70-regression-s70-final-important-1920.png、1366.png、390.png；真实查询/截屏时间见s70-regression-s70-final-important-screenshots.json。最终移动端筛选区随窗口宽度折叠；补跑三尺寸1项通过，主站类型检查与构建、兼容构建通过，见s70-mobile-final.log、s70-build-mobile-final.log、s70-compatibility-mobile-final.log。既有截图在本轮新前缀输出，不覆盖原路径。部分接口/首屏涉及全库投影与请求预算，日志中的实际耗时不等于页面性能达标承诺；没有编造P95或全时可用率。

真实ECB工作流记录{workflow.get('event_id','待生成')}及冻结宏观快照{workflow.get('macro_snapshot_id','待生成')}。研究影响与个人关注/已读为真实用户操作验收记录，不是新官方事件。简报按真实日期生成可查证据，若当天没有官方发布则明确空状态；没有公司关系与市场结果时相应步骤不通过或为空。

Git工作区基线整体未跟踪，本轮未提交或重置，不声称已有可比较提交差异。核心变更和文件哈希清单见s70-delivery-files.json；人工检查了身份/许可、不可变快照、SQL参数、聚合降级、回填通知排除与运行身份保护。没有虚构26人实际签审。

仍未完成的真实业务验收：可信50条外文全文翻译、BLS访问恢复、SEC/FRED接入、真实AI执行、真实企业传导/关系样本、合法行情反馈、官方双端LIVE以及新连续24小时窗口最终结论。按需每日简报已运行，自动日刊调度尚未新增；公网生产部署和多用户权限不在本机交付结论内。
""")]
for filename,title,body in stages:
    (root/'docs'/filename).write_text(f'# 寰瞰 HUANKAN · {title}\n\n{body}\n'+verification,encoding='utf-8')
aliases={'S63-研发基线与上阶段验收复核.md':'S63-研发基线复核.md','S64-全球重要金融事件筛选验收.md':'S64-全球事件智能筛选验收.md','S65-全球官方宏观变化分析验收.md':'S65-全球宏观变化分析验收.md','S66-全球金融影响传导研究验收.md':'S66-金融影响传导研究验收.md','S67-事件生命周期与关注跟踪验收.md':'S67-事件生命周期验收.md','S68-每日全球金融简报质量验收.md':'S68-每日金融简报质量验收.md','S69-连续运行与LIVE推送验收.md':'S69-运行稳定性与LIVE验收.md'}
for name,target in aliases.items():
    (root/'docs'/name).write_text(f'# 本阶段报告入口\n\n本轮报告统一使用最终清单名称，实际验收记录见[{target}]({target})。\n',encoding='utf-8')
status=root/'docs/统一开发状态报告.md';text=status.read_text(encoding='utf-8');marker='\n## S63—S70 本轮实际交付'
if marker in text:text=text[:text.index(marker)]
text+=marker+f"（{audit.get('queried_at','本轮')}）\n\n真实重要事件评分快照及复算、宏观口径检查、证据约束影响研究、事件关注/个人状态/站内更新、简报质量升级已编码并在本机运行。新增V49—V51，旧48迁移与原始84616条基线保持。完整回归146后端、19前端、3观察审计通过，最终改动相关12及简报3复测通过；浏览器34用例首轮{browser_result('s70-browser-regression.log')}，独立修复复测{browser_result('s70-browser-repaired.log')}，隔离SSE1通过。真实备份恢复及V51升级通过。\n\n新健康窗口{continuity.get('file','待启动')}，双浏览器窗口{online.get('file','待启动')}，均独立观察，不提前称24小时或真实LIVE通过。50条全文译文、BLS/SEC/FRED/AI配置、真实公司关系与合法行情仍缺失；每日简报目前按需生成，自动日刊调度尚未新增。完整结论与逐项限制见[S70产品级综合验收](S70-产品级综合验收.md)，S63—S69报告均已新增。\n"
status.write_text(text,encoding='utf-8')
print('S63—S70报告与统一状态已更新，以真实快照和日志为准。')
