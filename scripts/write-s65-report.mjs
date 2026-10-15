import {readFileSync,writeFileSync,readdirSync,statSync,existsSync} from 'node:fs';import {createHash} from 'node:crypto';
const read=p=>JSON.parse(readFileSync(p,'utf8'));const beijing=s=>new Date(Date.parse(s)+8*3600000).toISOString().replace('Z','+08:00');
const db=read('docs/evidence/s65-database-final.json'),accept=read('docs/evidence/s65-real-acceptance.json'),integrity=read('docs/evidence/s65-integrity.json'),runtime=read('docs/evidence/s65-runtime-audit.json'),source=read('docs/evidence/s65-official-source-check.json'),nav=read('docs/evidence/s65-event-navigation.json'),plan=read('docs/evidence/s65-query-plan.json');
const backend=readFileSync('runtime/s65-backend-accepted.log','utf8');if(!backend.includes('Tests run: 173, Failures: 0, Errors: 0, Skipped: 0')||!backend.includes('BUILD SUCCESS'))throw Error('最终后端验收日志未通过');for(const [p,expected] of [['runtime/s65-browser-repair.log','9 passed'],['runtime/s65-browser-final-workflow.log','4 passed'],['runtime/s65-event-navigation-final.log','1 passed'],['runtime/s65-isolated-accepted-sse.log','1 passed']])if(!readFileSync(p,'utf8').includes(expected))throw Error('验收尚未通过 '+p);
const baseline=read('docs/evidence/s64-closure-runtime-before.json');const processes=baseline.processes.filter(p=>[43884,43704,43988,36400].includes(p.ProcessId)).map(p=>{const now=runtime.processes.find(x=>x.ProcessId===p.ProcessId);const epoch=now?Number(now.CreationDate.match(/\d+/)[0]):null;return {pid:p.ProcessId,expected_created_at:p.createdAt,current_created_at:epoch?new Date(epoch).toISOString():null,identity_preserved:epoch===Date.parse(p.createdAt)};});if(processes.some(p=>!p.identity_preserved))throw Error('原进程身份改变');writeFileSync('docs/evidence/s65-process-preservation.json',JSON.stringify({at:runtime.at,processes},null,2));
const overview=accept.overview.body,c=accept.sample.body.calculations.previous,a=accept.sample.body.observations[0],b=accept.sample.body.observations[1];let missing=0,gaps=0;const blocked=[];for(const d of accept.details){const rows=d.body.observations;for(let i=0;i<rows.length;i++){const cur=rows[i],prev=rows[i+1];let reason=null;if(!prev){missing++;reason='没有已存前值';}else if(cur.metadata?.FREQ==='D'&&(Date.parse(cur.observation_date)-Date.parse(prev.observation_date)!==86400000)){gaps++;reason='日历不连续，未取得官方营业日日历';}if(reason)blocked.push({series:cur.series_id,evidence_id:cur.evidence_id,observation_period:cur.observation_date,previous_id:prev?.evidence_id||null,reason});}}
if(blocked.length!==overview.non_comparable)throw Error('受阻记录统计不一致');writeFileSync('docs/evidence/s65-non-comparable.json',JSON.stringify({at:new Date().toISOString(),scope:overview.audit_scope,count:blocked.length,missing_previous:missing,calendar_gaps:gaps,records:blocked},null,2));
const report=`# 寰瞰 HUANKAN · S65 全球官方宏观变化分析验收

S65开发及本机技术验收完成。复用S64排序、现有宏观账本、证据、关注、事务性Outbox与SSE；本轮没有开发S66—S70，没有重建数据库或重启正式后台。官方完整历史vintage、正式双端LIVE及当前连续24小时仍未通过，不能用技术回归替代。

## 入口与范围

- 工作台：[全球宏观变化分析](http://127.0.0.1:5179/#/macro)，API18086，最终JAR为 \`huankan-s65-accepted-0.1.0.jar\`。
- [真实可比较案例](http://127.0.0.1:5179/#/macro?series=EXR.D.USD.EUR.SP00.A&from=2026-10-08&to=2026-10-09)。所选范围最新值是10月9日，不冒充全库最新观测；数据库美元参考汇率最新观测为10月12日。
- 新研究服务关闭采集、所有后台调度与Flyway。正式18080、原研究18083、S64研究18085及PostgreSQL5432保持；本轮只更新新建18086研究服务。
- 原5173/5176/5178页面保留兼容入口；新分析在5179置于主视图，旧研究与采集能力收纳在可展开区域。没有新建重复业务路由。

## 真实数据分类审计

正式库只读快照：${db.at}。初始快照另保存在 \`evidence/s65-baseline.json\`；数量随采集变化，不以113981作为实时常数。

|数据类别|实际数量与口径|
|---|---|
|官方原始公告记录|${db.counts.official_announcements}，排除宏观观测；保留原始文档身份，不冒称去重后独立重大新闻|
|官方宏观指标序列|${db.counts.series}，均为ECB_DATA；已接入结构化官方宏观来源${overview.official_sources}|
|宏观观测记录|${db.counts.observations}；原始CSV逐条字段核验通过${overview.verified_observations}|
|历史观测本地版本|${db.counts.history_versions}，macro_history_version账本|
|官方可证实宏观修订|${db.counts.official_revisions}；本地变化版本${integrity.local_changed_versions}|
|GDELT聚合线索|${db.counts.gdelt}；不计官方公告或已核实原子事实|
|已核对原子字段事实|${db.counts.verified_facts}（FIELD_MATCHED）；SOURCE_CHECKED 1052；独立支持事实0|
|宏观研究快照|${db.counts.snapshots}，不计新公告或LIVE|
|存在可比历史变化的序列|${overview.comparable_series}|
|可计算变化记录|${overview.calculable_changes}|
|受阻比较记录|${overview.non_comparable}：无前值${missing}，日历不连续${gaps}|
|真实指标观测与事件证据关系|${integrity.linked_observations}；同一evidence_id与原始系列记录，不是${integrity.linked_observations}条政策公告关系|
|正式Fixture|${integrity.fixture}|

计算统计按每条序列最近100个不同观测期进行；本次最长序列不足100期，因此覆盖当前267条观测。当前观测表有267条本地版本记录，独立历史请求账本有119条；两个账本不相加为不同官方修订。原子事实263条与运行时CSV核验267条是不同口径。

受阻记录逐ID保存于 [s65-non-comparable.json](evidence/s65-non-comparable.json)。没有官方营业日日历时，不把跨周末/其他日历缺口强算成连续日变化；这不等同认定源数据错误。三条政策利率当前各只有一个观测，显示“暂无可比数据”。

## S65-A、B：指标治理与十进制变化

复用macro_series、macro_observation及原始响应。新接口从不可变原始CSV读取KEY、UNIT、UNIT_MULT、FREQ、币种/分母、季调和定义字段，核验数据库日期、原值、状态及SHA-256。最新4条观测尚无原子字段记录，动态源端字段核验补齐读取能力，没有伪造fact_candidate或提高独立核验等级。NBS RSS/FED标题不作为数值输入。

新规则 \`宏观变化-2.0\`：相同序列、单位、倍数、币种方向、频率、季调、指数基期与统计范围才允许比较；定义BREAKS、缺失值或未核验中间期阻止计算。支持上一期、月环比、季环比、年同比及明确起日区间比较；按照频率适用，年度/区间中间观测缺失也阻止计算。已核对COLLECTION=E的政策变更日序列按变更频率处理，不补造每日利率。

原始BigDecimal不舍入；相对变化 \`(current−previous)×100/previous\` 保留12位小数、HALF_UP。利率和百分比水平输出百分点与基点，相对百分比不适用。非正基期或负数量保留可解释绝对变化，限制缺乏经济意义的相对变化；负政策利率允许，参考汇率非正值不参与计算。前端浮点仅用于绘制位置，不计算业务变化值。

每个结果返回当前/前值ID、原始值、单位、公式、结果、规则版本及计算时间；同一响应附带原始证据、实际元数据与采集时间。兼容研究接口修正百分比单位及缺口处理，新的冻结快照规则升为 \`宏观同口径比较-1.2\`；既有1.1快照保留，没有改写历史输入或报告。

## S65-C：历史可得性与版本

分开返回观测期、官方发布、首次发现、采集、入库和本地版本。官方发布时间未知时保留null；官方vintage始终未知，显示“官方历史修订信息尚未取得”。asOf同时限制采集、入库及已知官方发布时间，只提供本地可知快照；不能据此复原最早公开时点或宣称精确历史实时回测。

历史请求返回真实动作、前一本地值、实际本地差异、版本、原始响应哈希与时间；Delete明确展示墓碑动作。当前本地变化版本0、官方修订0，不将Replace、重复HTTP获取、响应范围变化或重新计算冒充修订。FRED/ALFRED没有新配置或虚构Key。

## S65-D：S64实际事件联动

只确认macro_observation与financial_event共用原始证据ID、且CSV核验通过的来源元数据关系；不按标题关键词制造政策公告关联。无结构化证据的NBS事件显示“暂无可核验的宏观指标关联”。

实际双向工作流已经通过：宏观案例 → 原事件详情 → 指标工作台。另从S64筛选ECB_DATA进入真实事件 \`${nav[0].event}\`，再进入 \`${nav[0].href}\`，桌面和390px均通过。该边界是政策利率观测记录关系，不冒充另一个独立政策公告。只有一个值时仍保持空比较。

实际导航ID、时间、尺寸见 [s65-event-navigation.json](evidence/s65-event-navigation.json)。事件截图 [桌面](evidence/s65-event-1920.png)、[手机](evidence/s65-event-390.png)。研究重要性链接到S64已有冻结排序，不重建评分。

## S65-E：接口与数据库影响

|POST接口|能力与上限|
|---|---|
|/api/macro-changes/catalog|来源筛选、稳定排序、50条/页、实际最新数据库观测|
|/api/macro-changes/overview|真实数量、原始字段核验、可计算/受阻记录与最近来源任务异常|
|/api/macro-changes/detail|series/page/from/to/asOf/rangeStart；观测100条/页、版本100条/页、事件50条/页；定义、原值、前值、计算、证据、来源状态|

不存在记录返回正常空状态；负分页、倒置范围及未来截止时间拒绝。原研究、快照、证据查询与关注API继续复用。新统计/详情是查询，不插入金融事件、Outbox或计算提醒。

新增Flyway迁移：0。正式库仍为51个迁移；旧数据库校验与迁移文件无变更。现有macro_latest、macro_history_asof、系列唯一键、原始证据主键和响应关联索引复用。实际执行计划查询仅涉及目标宏观序列及对应raw_evidence主键，没有扫描GDELT新闻全表；单次执行 ${plan[0]['Execution Time']}ms。最终概况核验 ${accept.overview.elapsed_ms.toFixed(1)}ms，不能以单次测量宣称P95达标。计划及索引见 [查询计划](evidence/s65-query-plan.json)、[运行数据库诊断](evidence/s65-runtime-database-diagnostics.json)。

## S65-F：中文页面与真实案例

延续原深色终端主题和原/macro路由。六部分为真实总览、指标目录、变化、证据/本地版本、关联事件、规则研究解释。中文日期控件及北京时间复用；来源原始英文名称/代码保留。正文14px，1920×1080、1366×768、390×844类型/浏览器检查及截图均通过，已目视核对，无横向溢出。

可复核案例（两期原站再次返回HTTP ${source.status}，与数据库值一致）：

|项目|实际值|
|---|---|
|指标|EXR.D.USD.EUR.SP00.A；欧洲央行欧元兑美元官方参考汇率|
|当前案例观测|${a.observation_date}，${c.current_value}|
|上一可比观测|${b.observation_date}，${c.previous_value}|
|官方单位与方向|UNIT=USD、UNIT_MULT=0、FREQ=D、CURRENCY_DENOM=EUR；每1欧元对应美元数量|
|绝对变化|${c.delta} USD/EUR|
|相对变化|${c.relative_pct}%|
|当前证据ID|${c.current_id}|
|前值证据ID|${c.previous_id}|
|当前原始证据SHA-256|${a.raw_content_hash}|
|前值原始证据SHA-256|${b.raw_content_hash}|
|当前官方发布时间|${a.source_published_at??'未知；不使用观测日或Last-Modified替代'}|
|当前首次发现 / 采集 / 入库|${a.available_at} / ${a.collected_at} / ${a.ingested_at}|
|前值首次发现 / 采集 / 入库|${b.available_at} / ${b.collected_at} / ${b.ingested_at}|
|独立复算|BigInt十进制缩放、12位HALF_UP，与后端一致|

原站只读核验：${source.url}，${source.bytes}字节，SHA-256 ${source.sha256}。Last-Modified为 ${source.last_modified}，不是两期观测的最早公布时间。新请求只取两期，与数据库原始90日响应范围不同，哈希差异不能算修订。原站CSV保存在 [s65-official-source.csv](evidence/s65-official-source.csv)。没有写入正式数据。

原始完整结果：[s65-real-acceptance.json](evidence/s65-real-acceptance.json)；独立复算：[s65-independent-recalculation.json](evidence/s65-independent-recalculation.json)。所有观测有证据ID及哈希；新响应不替换旧原始字节。

截图：[1920工作台](evidence/s65-1920.png)、[1366工作台](evidence/s65-1366.png)、[390工作台](evidence/s65-390.png)，[1920中文曲线与证据](evidence/s65-chart-1920.png)、[1366中文曲线](evidence/s65-chart-1366.png)、[390中文曲线与证据](evidence/s65-chart-390.png)。时间/观测期见s65-screenshot-1920/1366/390.json。没有覆盖以前阶段截图。

## S65-G：更新观察基础

复用现有关注目标、事务性Outbox、SSE与资格账本，区分历史回填、重复采集、本地计算及官方更新。新查询不产生新消息。隔离双客户端事务提交、离线补读去重和历史不提醒通过；300条隔离观测不进入正式库。未新建变化阈值配置页面、自动交易或策略；真实官方LIVE样本仍为0，不能用隔离测试或个人研究通知替代。

## S65-H：实际测试记录

根目录先加载scripts/load-local-env.ps1，使用Java21；数据库Fixture仅在55432/huankan_test的独立schema中。清理脚本已执行。

|实际命令|最终结果与日志|
|---|---|
|mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml -B '-Dhuankan.jar.name=huankan-s65-accepted-0.1.0' package|173通过，失败/错误/跳过0；runtime/s65-backend-accepted.log|
|前端npm.cmd test|19通过；runtime/s65-frontend-delivery.log|
|前端npm.cmd run build|类型检查及主入口构建通过；runtime/s65-build-delivery.log|
|npx.cmd vite build --config vite.s65.config.ts|新研究入口构建通过；runtime/s65-sidecar-build-delivery.log|
|node node_modules/@playwright/test/cli.js test --config playwright.s65.config.ts|首轮42：38通过4失败；runtime/s65-browser.log|
|同配置 macro-changes.spec.ts real-feed.spec.ts s30-governance.spec.ts s46-workbench.spec.ts|修复9通过；runtime/s65-browser-repair.log|
|同配置 s63-s70.spec.ts|最终研究工作流4通过；runtime/s65-browser-final-workflow.log|
|同配置 event-macro.spec.ts|新增重要事件跳转1通过；runtime/s65-event-navigation-final.log|
|start-isolated-s65-web.ps1 → playwright-s65-isolated.config.ts → stop-isolated-s65-web.ps1|最终JAR隔离SSE1通过、schema清理；runtime/s65-isolated-accepted-sse.log、accepted-cleanup.log|
|node --test scripts/acceptance-quality.test.mjs|观察审计3通过；runtime/s65-observation-tests.log|

浏览器43个不同用例均有当前轮最终通过记录；不同批次有重叠，不把38+9+4+1加成52个独立用例，也不宣称一次全量全绿。后端新增20个计算用例与2个数据库用例已包含在173项中，专项21项不另加。此前历史150/17/37数字不是本轮最终数量。

覆盖25项验收需求：单位/汇率方向/百分比和基点/分母零/负值/精度/排序/频率/季调/缺口/月季年比较由MacroChangeEngineTest及既有MacroComparisonTest覆盖；版本/vintage/删除/原始哈希由既有历史数据库用例覆盖；证据关系/无证据空状态/分页由S65数据库和浏览器覆盖；300条隔离历史连续三页各100且不重复，第四页空，真实执行计划另保留；中文、空状态、三尺寸、事件跳转由6个新增浏览器用例覆盖；SSE、回填门禁、Fixture隔离与历史证据保全由数据库、隔离SSE及本轮正式只读审计覆盖。

失败日志保留：初始专项缺许可必填字段、错误尝试修改隔离不可变观测（改为验证拒绝修改）；首次API空来源参数500（已加text类型）；BLS中文百分比兼容映射遗漏（已补）；浏览器首次SSE等待超时、收纳后旧控件不可见、审计响应等待、多个历史研判的定位歧义，以及新增手机筛选未展开的测试超时（已修正实际操作）。失败上下文见evidence/s65-browser-first-failures，没有删除失败来虚构通过。

## 原始证据、Git与运行观察

原始宏观观测字节哈希错误${integrity.macro_hash_errors}；历史响应字节哈希错误${integrity.history_response_hash_errors}。84616条既有基线逐ID/来源/URL/存储内容哈希字段复核，缺失或变更${integrity.historical_identity.missing_or_changed}。旧迁移校验及文件变更${integrity.migration_changes.length}/${integrity.migration_file_changes.length}。本轮没有再次对全部GDELT正文做重型字节扫描；全量字节保全历史结果引用S64原审计，本轮原始字节重算范围是宏观与历史响应。不能把身份/哈希字段复核冒充本轮全库字节重算。

Git仍整体未跟踪；未提交、重置或覆盖用户修改。git diff --check无输出不足以替代人工审查；已逐项复核参数绑定、限量查询、原始CSV/hash校验、缺口屏障、单位/频率门禁与空状态。实际修改文件及SHA-256见evidence/s65-files.json。

运行审计时间${beijing(runtime.at)}：主采集43884、原研究43704、健康观察43988及双浏览器36400的PID与创建时间和S64前快照一致，身份保全4/4；健康HTTP200/UP。健康采样继续且新鲜，仍保留08:52:26与08:54:04的102457ms/98676ms缺口，未拼接窗口，未宣布当前连续24小时通过。两端最近已连接/可见，官方合格LIVE0；原08:47列表超时1条，另有${runtime.browser.errors}份连接状态不足采样，包含超时重连/热更新期间状态，不能称浏览器从未断开。

采样程序未被本轮重启；最新样本仍推进，不支持“目前卡死”的结论。两次旧缺口原因尚未确定，不能直接认定应用停机或测试负载导致。数据库现场诊断无持续活动长查询，宏观有界执行计划约1ms；不据此排除缺口发生时网络、资源或浏览器调度问题。24小时来源失败逐来源/错误码及HTTP字段保留在s65-runtime-database-diagnostics.json，最新宏观来源异常0不等于全天来源无失败。原数据库服务未操作。

## 剩余限制

官方完整历史vintage和可证实宏观修订仍为0；没有官方营业日日历，日频跨日历缺口严格阻止比较；三条利率单值、同比对齐历史不足均保持空状态。未把参考汇率当成交价格或数学变化当已证实市场影响。未配置真实AI、市场价格/一致预期与企业敞口时不生成对应结论。NBS/FED没有新增未经核验结构化数值；BLS/FRED外部访问/凭证限制保留。当前来源成功、浏览器回归与隔离SSE不能替代正式LIVE、全来源可靠性或完整连续运行验收。
`;
writeFileSync('docs/S65-全球官方宏观变化分析验收.md',report);
const statePath='docs/统一开发状态报告.md';let state=readFileSync(statePath,'utf8');const marker='## 2026-10-13 S65 全球官方宏观变化分析补齐';const i=state.indexOf(marker);if(i>=0)state=state.slice(0,i);state+='\n\n'+marker+'\n\n'+`S65开发与本机技术验收完成，独立入口http://127.0.0.1:5179/#/macro、API18086；采集/调度/Flyway关闭。新增原始CSV逐条核验、十进制规则2.0、历史本地版本差异、S64事件证据双向跳转及中文工作台，兼容快照修正规则1.2，保留旧1.1。没有自动进入S66—S70。\n\n${db.at}正式快照：官方原始公告记录${db.counts.official_announcements}（排除观测）、宏观序列7/观测267、历史本地版本119、可证实官方修订0、GDELT线索${db.counts.gdelt}、FIELD_MATCHED原子事实263/独立支持0、宏观快照${db.counts.snapshots}。267条原始CSV核验通过；4条序列有208项连续历史变化，59项受阻（7无前值、52日历不连续）。真实案例2026-10-09/08欧元兑美元1.1206/1.1186，差0.0020 USD/EUR、相对0.178794922224%，原站HTTP200及独立BigInt复算一致。267个指标观测与事件共用证据关系不等于267条政策公告。\n\n新增迁移0，51个既有迁移不变；宏观及历史响应字节哈希错误0，84616历史基线身份/存储哈希字段变更0，正式Fixture0。后端最终173、前端19、观察审计3及最终JAR隔离SSE1通过；浏览器首轮42项38通过4失败，复测9及最终工作流4、新增事件跳转1全部通过，43个不同用例有最终通过记录，不重复相加。三尺寸截图、中文验收报告、实际命令及失败日志见[S65验收](S65-全球官方宏观变化分析验收.md)。\n\n原43884/43704/43988/36400进程PID与创建时间保持；正式后台与共享数据库未重启，已有前端可能因源码热更新重载。08:52/08:54采样缺口及08:47浏览器超时保留，正式双端LIVE0，当前24小时仍待验收；官方完整vintage、合法行情/AI/企业敞口等缺口不变。来源最近成功不能覆盖24小时失败，详见本轮运行诊断。\n`;
writeFileSync(statePath,state);
const walk=p=>readdirSync(p).flatMap(n=>{const q=p+'/'+n;return statSync(q).isDirectory()?walk(q):[q]});const files=[...['MacroChangeController','MacroChangeService','MacroChangeEngine','MacroComparison','MacroResearchService'].map(n=>'backend/src/main/java/cn/huankan/event/'+n+'.java'),'backend/src/test/java/cn/huankan/event/MacroChangeEngineTest.java','backend/src/test/java/cn/huankan/evidence/DatabaseGateTest.java',...['MacroChanges','ResearchDesk','EventResearchDetail'].map(n=>'frontend/src/components/'+n+'.vue'),'frontend/src/App.vue','frontend/vite.s65.config.ts','frontend/playwright.s65.config.ts','frontend/playwright-s65-isolated.config.ts',...walk('frontend/e2e-s65'),...walk('frontend/e2e-s65-isolated'),...walk('scripts').filter(p=>/s65/.test(p)),...walk('runtime').filter(p=>/^runtime\/s65.*\.mjs$/.test(p)),'docs/S65-全球官方宏观变化分析验收.md',statePath];const hash=p=>({path:p,bytes:statSync(p).size,sha256:createHash('sha256').update(readFileSync(p)).digest('hex')});const generated=walk('docs/evidence').filter(p=>p.split('/').some(n=>n.startsWith('s65-'))&&p!=='docs/evidence/s65-files.json');writeFileSync('docs/evidence/s65-files.json',JSON.stringify({at:new Date().toISOString(),git_state:'全项目未跟踪；本轮未提交或重置',new_migrations:[],files:[...new Set(files)].map(hash),evidence:generated.map(hash)},null,2));console.log(JSON.stringify({report:'docs/S65-全球官方宏观变化分析验收.md',files:files.length,evidence:generated.length,counts:db.counts,blocked:blocked.length,processes_preserved:processes.every(p=>p.identity_preserved),browser_unique:43}));


