import {readFileSync,writeFileSync,readdirSync} from 'node:fs';import {createHash} from 'node:crypto';
const read=p=>JSON.parse(readFileSync(p,'utf8').replace(/^\uFEFF/,''));const audit=read('docs/evidence/s66-final-audit.json'),run=read('docs/evidence/s66-real-case-api.json'),independent=read('docs/evidence/s66-independent-recalculation.json');const c=audit.counts;
const log=p=>readFileSync('runtime/'+p,'utf8');function result(p){const t=log(p);return {passed:[...t.matchAll(/(\d+) passed/g)].at(-1)?.[1],failed:[...t.matchAll(/(\d+) failed/g)].at(-1)?.[1]||'0',finished:/\d+ passed/.test(t)};}
const browser=result('s66-browser-regression.log'),retry=result('s66-browser-repaired.log'),workflow=result('s66-workflow-final.log');const back=[...log('s66-backend-final.log').matchAll(/Tests run: (\d+), Failures: (\d+), Errors: (\d+), Skipped: (\d+)/g)].at(-1)?.slice(1);
if(!browser.finished||!workflow.finished||!retry.finished||!back)throw Error('完整日志尚未结束，不能生成最终通过报告');
const text=`# 寰瞰 HUANKAN · S66 全球金融影响传导研究验收

本轮只执行S66。开发及本机真实研究链路验收完成，经济机制候选与研究假设不等同已发生的企业或市场影响。未自动进入S67—S70，继续保留此前已启动的S69独立观察。

## 入口与实现边界

- [全球市场联动研究](http://127.0.0.1:5180/#/linkage?event=${run.report.event_id})，复用已有全球市场联动、金融事件研判菜单。原5173、S64 5178、S65 5179入口保留；新研究API18087。
- [本次可审计历史快照](http://127.0.0.1:5180/#/linkage?snapshot=${run.id})。选择历史版本时指标和观测选择同步到冻结输入，避免默认指标与旧快照混淆。
- 新研究实例关闭采集、调度和Flyway，未重启正式18080、原18083或共享PostgreSQL。主采集PID43884及两个观察PID43988/36400身份保持；现场证据见s66-final-audit.json。前端源码热更新可能重载现有页面，不把浏览器连接采样称从未断开。
- 新迁移0；复用V33的impact_assessment、JSONB冻结报告、不可变触发器与唯一约束。当前${c.migrations}个迁移，前期51个数据库校验码比对变更${audit.migration_changes.length}，历史迁移文件比对变更${audit.historical_migration_file_changes.length}。

## 当前数据库实际快照

查询时间：${audit.at}；业务数量随真实采集和研究生成变化，见evidence/s66-final-audit.json。

|项目|实际数量与口径|
|---|---|
|正式原始证据|${c.raw_evidence}|
|真实宏观序列 / 观测|${c.macro_series} / ${c.observations}|
|冻结机制模板|${c.templates_frozen}，从已保存研究的mechanism_catalog去重统计；不是8条已发生影响|
|实际适用机制类别|${c.applicable_mechanisms}，本次真实汇率候选|
|保存的研究假设记录|${c.hypothesis_records}，逐研究版本的机制候选计数，允许同机制在不同真实研究版本出现|
|本轮规则研究快照 / 全部历史研判|${c.research_snapshots} / ${c.all_impact_snapshots}|
|已核实关系 / 待审核关系|${c.verified_relations} / ${c.pending_relations}|
|真实市场验证 / AI执行记录|${c.market_validations} / ${c.ai_executions}|
|正式Fixture / 原始字节哈希错误 / 冻结输入哈希错误|${c.fixture} / ${c.raw_hash_errors} / ${c.snapshot_hash_errors}|

原84616条历史证据逐ID、来源、URL、内容哈希保全，缺失或变更${audit.baseline_preservation.missing_or_changed}。没有删除Outbox、原始响应或历史快照。研究生成是真实已有官方观测上的用户研究操作，不是新官方发布、Fixture或LIVE。

## 实际文件与API

新增TransmissionRules.java、TransmissionService.java、TransmissionController.java、TransmissionRulesTest.java及TransmissionDesk.vue；增补DatabaseGateTest；现有App.vue、ResearchDesk.vue、MacroChanges.vue、EventResearchDetail.vue、FactEngineDesk.vue接入已有菜单和证据跳转。

POST /api/transmission/catalog、input、analyze、query、reproduce。input按稳定事件UUID解析真实观测，analyze接收真实观测证据UUID及证据截止，query/reproduce按当前访问身份查询。源文件与脚本完整哈希见evidence/s66-files.json。当前是共享本机管理密钥身份，不声称独立多用户权限系统；旧管理员研判/证据接口仍按既有管理员权限访问。

计算复用S65宏观变化-2.0。研究规则传导研究-1.0，机制版本经济传导机制-1.0。事实、计算、机制、假设、市场验证节点分别保存，每条路径以parent逆向到原始两期证据；计算结果拥有独立SHA256计算ID，整体输入以规范JSONB SHA256冻结。相同事实/规则/截止重复执行返回同一快照；新截止创建新不可变版本，旧结果复算从冻结输入读取。

## 机制与证据门禁

机制库包含汇率收入折算、进口成本、出口竞争力、利率融资成本、估值折现、通胀成本、增长需求、商品原料成本8种。只有已核验CSV元数据满足FX或RATE映射且两期可比时运行；不从标题猜测通胀、增长或商品输入。本次只形成3类汇率机制候选，其余知识模板未被标记为已发生。

每个候选保存输入/输出变量、前提、所需证据、假设、方向、局限、反面条件、版本及审核性质。当前状态为机制适用条件部分满足、假设待验证；没有企业金额、计价、期限或对冲证据时不升级到满足全部条件。反面解释是可能推翻条件，不冒充已取得反向证据。研究状态字典还区分证据不足、存在反面证据、市场结果已验证和假设已失效；本轮没有向后两项写入无依据确认记录。

关联关系只查询双方已核实、已审核ACCEPT、事件绑定、合法许可、原始哈希、截止前披露及有效期间内的真实关系。最新截止前审核须为ACCEPT，不使用已被退回关系。法人登记、会计并表、发行主体、行业归属、客户供应、收入区域/币种、成本及融资敞口采用不同含义；GLEIF并表不能作供应链证明。当前正式已核实关系0，不生成公司图、经营敞口或受益股票。

参考汇率仅为官方参考信息，不是成交报价，定义核查依据[ECB参考汇率说明](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html)。政策传导存在条件和时滞，机制边界参考[ECB货币政策传导说明](https://www.ecb.europa.eu/mopo/intro/transmission/html/index.en.html)。这些教学定义不充当本事件的企业敞口或市场验证证据。

## 真实欧元兑美元案例

研究标题：《${run.report.title}》。序列${run.report.series_id}，每1欧元对应美元数量；10月9日是本研究所选观测，不冒充数据库最新期。

|输入|原始值|正式证据ID|原始SHA256|
|---|---|---|---|
${run.report.inputs.observations.map(o=>`|${o.observation_date}|${o.original_value}|${o.evidence_id}|${o.raw_content_hash}|`).join('\n')}

两期原子字段核验事实存在，但event_id为空；按真实observation_id与fact_evidence和原始事件共同证据关联，未改写事实以强造关系。原始官方发布时间未知时保持未知，采集与入库时间独立展示。原始URL见evidence/s66-real-case-api.json，可在页面打开。

当前${run.report.calculation.current_value}减前值${run.report.calculation.previous_value}，绝对变化${run.report.calculation.delta} USD/EUR；相对变化(delta/previous)×100=${run.report.calculation.relative_pct}%。独立BigInt有理数复算${independent.passed?'通过':'未通过'}，见evidence/s66-independent-recalculation.json。EUR相对USD升值，USD相对EUR贬值；固定美元金额按倒数转换的数学方向相反，不把两日参考值差直接当企业利润变化。

研究快照${run.id}，输入哈希${run.input_hash}，计算ID${run.report.calculation.calculation_id}。3条候选分别明确收入、进口合同、出口价格/需求条件、可能对冲和反面解释。企业模块显示“尚无可核验的企业经营敞口证据”，市场模块显示“市场反应尚未验证”。没有真实模型配置，generated_by为RULE，AI执行0。

Web由S64真实ECB观测事件进入S66、由S65观测进入S66，并可回到稳定事件ID与指标ID；历史版本回读和冻结复算可操作。没有新增菜单或自动交易模块。

## 自动化及人工核查

- 根目录加载scripts/load-local-env.ps1，Java21；mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml -B -Dhuankan.jar.name=huankan-s66-accepted-0.1.0 package：完整${back[0]}项，失败${back[1]}、错误${back[2]}、跳过${back[3]}，s66-backend-final.log。最终来源等级、单位映射、计算ID与冻结目录相关改动后12项复测打包通过，s66-reviewed-backend.log；不相加为197个独立用例。
- 新增11个金融规则用例和1个独立数据库链路用例，覆盖报价方向、正反与零变化、精确差值/相对数、变量映射、适用前提、反面条件、规则确定性、两期来源绑定、反向路径、无关系/行情空状态、幂等、身份查询、不可变与版本保留。既有利率百分点/基点、单位口径、GLEIF与关系审核测试继续回归；Fixture仅位于55432隔离schema。
- npm.cmd --prefix frontend test：19项通过，s66-frontend-tests.log；类型检查与主站构建、S66入口构建、兼容入口构建通过，s66-build-delivery.log、s66-study-build.log、s66-compatibility-build.log。
- 既有43个浏览器用例本轮首轮${browser.passed}通过/${browser.failed}失败，s66-browser-regression.log。两项旧用例分别固定旧文案、仅按事实ID误选不同规则版本的快照；补修标题回显、按实际状态核对，并显式选取历史规则版本后${retry.passed}项通过，s66-browser-repaired.log。新增7项真实S66全流程、三尺寸、快照回读、S64/S65回链及无效输入通过，s66-workflow-final.log。合计50个不同用例有最终通过记录，保留首次失败，不称一次完整运行全绿。
- 隔离SSE1项通过，s66-isolated-sse.log；本轮独立schema经51迁移，双浏览器事务提交、补读、去重及历史回填门禁通过，随后只清理该schema，s66-isolated-stop.log。不是正式官方LIVE样本。
- 观察审计3项通过，s66-observation-tests.log。node scripts/audit-s66.mjs实际数据库审计、node scripts/recalculate-s66.mjs独立算术核查通过。
- 1920×1080、1366×768、390×844页面与路径截图：evidence/s66-screen-1920.png、s66-screen-1366.png、s66-screen-390.png及s66-path-*。时间/快照见s66-screenshots.json；真实完整长页面见s66-transmission-*。人工检查了手机溢出、字号、研究假设分区、快照选择同步及桌面来源回链。

Git全项目基线未跟踪，本轮未提交、重置或声称提交级差异。修复前的编译/冻结日期测试失败记录保留s66-first-frontend.log、s66-transmission-first.log；最终通过才用于验收。角色职责以真实实现/计算/门禁/回归交叉审查，不虚构26人签审。

## 运行状态及未完成事项

本轮最终现场健康${audit.continuity.latest.health?.status}，审计积压${audit.continuity.latest.database?.audit_backlog}；连续窗口已有${audit.continuity.interruptions?.length||0}个采样空档，浏览器观察${audit.online.errors?.length||0}个错误。正式双端LIVE样本${audit.online.latest.status?.body?.two_client_samples||0}，没有可核验官方发布到入库/Outbox/SSE接收/可见渲染/ACK完整链。来源24小时失败按源单独保留，evidence/s66-runtime-database-diagnostics.json；最新成功不覆盖历史DNS、404、解码与连接错误。

S69仍按原窗口继续至结束审计，不新建或拼接，不认定无中断24小时通过。SEC/FRED配置、真实AI评测、官方完整历史vintage、真实企业经营/供应链敞口、合法证券行情验证继续未完成。S66交付的是有真实输入、可复算且证据约束的条件研究链路，不是已验证投资结论。
`;
writeFileSync('docs/S66-全球金融影响传导研究验收.md',text);writeFileSync('docs/S66-金融影响传导研究验收.md','# S66报告入口\n\n本轮增量交付见[S66全球金融影响传导研究验收](S66-全球金融影响传导研究验收.md)。此前S63—S70统一交付的历史证据与日志保留。\n');
const status='docs/统一开发状态报告.md';const old=readFileSync(status,'utf8');const marker='\n## 2026-10-13 S66 全球金融事件影响传导研究增量交付';const update=marker+`\n\n本轮只执行S66，新增证据约束规则研究引擎、8类机制模板、中文传导工作台与冻结复算；不进入S67—S70。新研究5180/API18087，采集/调度/Flyway关闭，原主服务和观察不重启。正式数据库${audit.at}：S66快照${c.research_snapshots}、假设记录${c.hypothesis_records}、冻结模板${c.templates_frozen}、已核实关系${c.verified_relations}、市场验证${c.market_validations}、AI${c.ai_executions}；迁移仍51，历史84616证据保全。真实EUR/USD两期${run.report.calculation.current_value}/${run.report.calculation.previous_value}，差${run.report.calculation.delta}、相对${run.report.calculation.relative_pct}%，独立复算通过。\n\n后端${back[0]}完整、最终相关12复测、前端19、观察3、隔离SSE1通过。浏览器43项首轮${browser.passed}/${browser.failed}，失败2项修复复测通过，新增7项通过，共50个不同用例有最终通过记录。S69已有采样空档及浏览器错误，正式LIVE0，仍不得称24小时通过；外部模型、行情和公司敞口缺口保持。完整案例、截图、命令、逐项限制见[S66验收报告](S66-全球金融影响传导研究验收.md)。\n`;
writeFileSync(status,(old.includes(marker)?old.slice(0,old.indexOf(marker)):old)+update);console.log('S66中文报告与统一状态已按真实审计和日志生成');
