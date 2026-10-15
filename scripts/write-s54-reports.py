from pathlib import Path
import json, datetime

root=Path(__file__).resolve().parent.parent
def read(name):return json.loads((root/'docs/evidence'/name).read_text(encoding='utf-8-sig'))
state=read('s54-final-state.json');quality=read('s54-practical-quality.json');business=read('s54-business.json');ledger=read('s54-ledger-integrity.json');backup=read('s54-backup-restore.json');tests=read('s54-test-summary.json');continuity=read('s46-continuity-v4.json');browsers=read('s46-online-browser-observation-v2.json');primary=read('s54-primary-preserved.json');orphan=read('s54-orphan-recovery.json');byte_audit=read('s54-formal-byte-audit.json')
counts=state['counts'];now=datetime.datetime.now(datetime.timezone.utc).isoformat();elapsed=continuity.get('observed_seconds',0);interruptions=len(continuity['interruptions']);sse_log=(root/'runtime/s54-isolated-sse-accepted.log').read_text(encoding='utf-8',errors='replace');sse_pass='1 passed' in sse_log
if tests['total']!=134 or tests['errors'] or tests['failures'] or tests['skipped']:raise RuntimeError('后端测试未满足真实验收条件')
if not backup['restore_passed']:raise RuntimeError('新版本恢复尚未通过')
if not sse_pass:raise RuntimeError('本轮隔离SSE尚未通过，禁止填写通过')
modified=[];start=datetime.datetime.fromisoformat('2026-10-10T08:40:00+00:00').timestamp()
for directory in ['backend/src','frontend/src','frontend/e2e-s54','frontend/e2e-s54-isolated','scripts']:
 for p in (root/directory).rglob('*'):
  if p.is_file() and p.stat().st_mtime>=start:modified.append(str(p.relative_to(root)).replace('\\','/'))
for f in ['backend/pom.xml','frontend/s54.html','frontend/vite.s54.config.ts','frontend/playwright.s54.config.ts','frontend/playwright-s54-isolated.config.ts']:modified.append(f)
modified=sorted(set(modified));(root/'docs/evidence/s54-modified-files.json').write_text(json.dumps({'checked_at':now,'method':'本阶段开始后的源码、脚本修改时间；工程现有未跟踪文件不作为本轮改动认定','files':modified},ensure_ascii=False,indent=2),encoding='utf8')
common=f'''## 真实验收快照

生成时间：{now}。正式库统计时间：{state['checked_at']}。采集仍在运行，后续计数会变化。

|项目|实际结果|
|---|---|
|数据库|本机 PostgreSQL 5432 / huankan，已应用48个Flyway迁移；测试使用55432 / huankan_test独立schema|
|原始证据 / 金融事件 / Outbox|{counts['raw_evidence']} / {counts['financial_events']} / {counts['outbox']}|
|原子事实|{counts['atomic_facts']}，字段核对{counts['field_checked']}，来源核查{counts['source_checked']}，独立支持{counts['independent_supported']}|
|正式Fixture / Outbox重复 / 来源绑定错误|{counts['fixture_records']} / {counts['outbox_duplicates']} / {counts['source_binding_errors']}|
|隔离 / 已核实企业关系 / AI实际执行|{counts['quarantine']} / {counts['verified_relations']} / {counts['ai_executions']}|
|本轮回归|后端134通过、0失败、0错误、0跳过；既有前端5通过；正式浏览器原10+新增3通过；隔离SSE 1通过|
|新版本备份恢复|{backup['restored']['migrations']}迁移，{backup['restored']['evidence_count']}证据，{backup['restored']['outbox_count']}Outbox；全部恢复哈希和引用错误0|
|官方日历|FED 76条，ECB 55条，合计131条；计划日期不等于已经发布|
|正式BLS / SEC数值|BLS文件观测0；SEC XBRL {quality['zero_checks']['sec_xbrl']}|
|持续运行|已有窗口起点{continuity['started_at']}，截至快照{elapsed}秒，{interruptions}条中断记录；不足24小时，待验收|
|真实官方LIVE双浏览器样本|{state['stream/browser/status']['two_client_samples']}，尚未最终通过|

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
'''
requests=quality['requests'];bls=[r for r in requests if 'bls.gov' in r['url']]
table='\n'.join(f"|{r['method']}|{r['url']}|{r['http_status']}|{r['response_bytes']}|{r['sha256']}|" for r in bls)
request_details='\n\n'.join(f"### {r['method']} {r['url']}\n\n请求时间：{r['requested_at']}；响应时间：{r['received_at']}。\n\n请求配置：`{json.dumps(r['request_info'],ensure_ascii=False)}`\n\n响应头：`{json.dumps(r['headers'],ensure_ascii=False)}`\n\n网络：`{json.dumps(r['network'],ensure_ascii=False)}`\n\n响应开头：\n\n```text\n{r['response_excerpt'][:300]}\n```" for r in bls)
calendar=quality['calendar_counts'];brief=business['brief']['body'];repeat=business['brief_repeat']['body'];sample=business.get('card_detail',{}).get('body',{});event=sample.get('event',{});proofs=sample.get('evidence',[])
cards_sample=f"官方标题：{event.get('original_title','无')}；来源：{event.get('source_id','无')}；事件ID：{event.get('id','无')}。\n\n"+'\n'.join(f"- 原始URL：{p['source_url']}；证据ID：{p['id']}；SHA-256：`{p['raw_content_hash']}`；发布：{p['published_at']}；采集：{p['collected_at']}；本地修订：{p['revision']}；真实字节校验：{p['hash_passed']}。" for p in proofs)
specific={
'S47-BLS访问诊断与合规恢复验收.md':f'''# S47 BLS访问诊断与合规恢复验收

结论：合规诊断与关闭失败入库已验证；实际访问恢复未通过。6次有限请求均HTTP403，新增正式BLS观测0，重复导入新增0，未将失败响应生成新闻或数值。失败响应作为HTTP诊断留存，不计为成功业务记录。

|方法|实际路径|HTTP|实际字节|实际响应SHA-256|
|---|---|---|---|---|
{table}

依据：[BLS官方API签名](https://www.bls.gov/developers/api_signature.htm)、[BLS官方开发者条款](https://www.bls.gov/developers/termsOfService.htm)、[BLS官方CPI文件目录](https://download.bls.gov/pub/time.series/cu/)。核查日期2026-10-10；资源使用范围、依据和检查时间已登记 `official_resource_policy`。仅留存原始公共统计响应、系列定义、脚注及数值，不使用徽标，不宣称背书，不改值冒充官方结果。

官方签名核对后，POST使用JSON `seriesid/startyear/endyear`，单系列GET使用明确官方路径。两个方法和公开文件都403，DNS解析与TLS握手有真实结果；不能据此断定IP封锁、地理封锁或特定防火墙根因。没有旋转UA、代理绕行或重复强攻。同URL24小时内只允许一次显式诊断，跨实例持久预约；正文最大4MiB/元数据2MiB/错误64KiB，并使用实际正文读取截止时间。

文件适配器仅接受有证据的CUUR0000SA0全项目未季调、月度、1982-84=100；拒绝未知脚注、重复月份和不匹配单位，M13年度平均不混入月度。精确TSV行、数值、脚注与系列/文件响应关联，哈希基于真实文件字节。每个同期修订保留新旧证据和事实版本；未知首次发布时间保留空，不把Last-Modified或采集时间变为发布时间。

隔离库已验证导入、重复0新增、修订保留以及事实提交失败时证据/事件/Outbox整体回滚。正式文件未能取得200，因此不宣称真实BLS文件导入通过。

{request_details}
''',
'S48-全球官方发布监控验收.md':f'''# S48 全球官方发布监控验收

结论：监控页面、真实日历证据和历史事件查询通过；真实官方新增双浏览器传播待验收。

真实HTTP200：ECB会议日历与美联储2026年10月日历，各原始HTML、HTTP头、DNS/TLS和SHA-256已保存。ECB55条明确日期；FED76条明确月内日期，其中74条保存原站计划时刻文本。日期来自真实HTML，不按固定频率推算。计划时刻文本与实际发布时间、采集时间独立；所在地时区只作为机构背景，未擅自换算未明确时区的原站时刻。ECB具体时刻未知，planned_at全部为空。美联储“approximate”说明保留为官方预估日期。

来源：[ECB官方会议日历](https://www.ecb.europa.eu/press/calendars/mgcgc/html/index.en.html)、[FED官方10月日历](https://www.federalreserve.gov/newsevents/2026-october.htm)。许可依据：[ECB版权条款](https://www.ecb.europa.eu/services/using-our-site/disclaimer/html/index.en.html)、[FED免责声明版权段](https://www.federalreserve.gov/disclaimer.htm)。不使用徽标、框架嵌入或第三方材料。

页面展示来源、最近真实标题、最近任务、HTTP状态、实际发布时间、首次发现、采集时间、最近成功检查和异常状态。可见监控页每30秒刷新数据库；不会把空值写成0。任务超时、最近失败和检查间隔异常表示需要核查，不能断言一定没有更新。日历页面有界100条、近期-7至+90天；完整原始131条仍在库中。FED当前范围仅2026年10月，未取得国家统计局可靠计划日历，不能补造未来月份或NBS计划。

同源document_id保持主事件并保留证据修订时间线；跨来源不凭名称相似强制合并。当前日历显式同步使用已保存、获准且24小时内有效响应做幂等解析；自动滚动月历及定时刷新全源日历尚未验收。

保持原双浏览器观察进程。官方LIVE接收与实际渲染双客户端样本为{state['stream/browser/status']['two_client_samples']}；历史回放及隔离测试与此分开。
''',
'S49-SEC披露数据工程验收.md':'''# S49 SEC披露数据工程验收

结论：前置工程已扩展，SEC正式披露采集仍待配置。实际联系邮箱为空、自动采集关闭，公司映射与关注名单不得填入来源不明的CIK；本轮没有发起SEC真实披露请求，正式XBRL表0条。

沿用真实官方公司映射搜索、CIK核验、个人关注公司、配置说明及SEC统一限流器。新增8-K、10-Q、10-K及更正表单定义；XBRL模型保留CIK、taxonomy、concept、单位、会计期间、accession、filed、财年、期间、frame、证据与版本，不混淆瞬时资产和期间流量。解析器严格校验CIK、单位、提交类型、accession与会计期间；报表比较必须同企业、同概念、同单位、同期间类型，不能把不同口径数字当作增长。

新增中文“SEC配置准备”页面和POST启用预检。原联系邮箱及真实UA须由用户在本地填写，不填虚构值、不索要密码或API Key。新数值模型与解析的测试仅在隔离环境，不证明真实SEC文档与XBRL数据闭环；真实公司事实接口证据持久化、披露到财务事实的联动仍须在有效配置后联调。

官方依据：[SEC公平访问与EDGAR数据说明](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)、[官方JSON接口](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)。2026-10-10核查官方当前上限10次/秒；本项目复用跨data.sec.gov/www.sec.gov统一预算，保守2次/秒和120次/分钟，既有403/429/5xx处理继续保留。
''',
'S50-金融事件研究卡片验收.md':f'''# S50 金融事件研究卡片验收

结论：真实官方事件卡片及原始证据查询可用，桌面与手机通过；市场反馈、独立共证与AI执行不作完成声明。

卡片列出原始/有真实翻译时的中文标题、机构、事件类型、官方时间、首次发现、采集时间、有效事实状态、事实字段、关键数字/观测期（确有结构化观测时）、同序列前期差值、规则优先级、条件性机制、关注市场、反向解释、缺失证据、后续变量、原始链接、证据版本和时间线。没有数值/实质内容的官方标题记录不能补造关键事实或公司受益名单。优先级为规则分数，不等同真实市场重要性。

来源确认、FIELD_MATCHED与INDEPENDENT_SUPPORTED分别呈现；当前独立支持0。机制和反向解释明确为规则假设，AI假设为空并显示未执行，市场状态为“尚未获得行情验证”。主题来自真实治理标签或已验证指标映射；半导体等无匹配专题显示暂无真实数据。

同来源和真实document_id保留一份主事件，证据所有版本保留，最多100条详情；列表每页50条。跨源的语义同事件归并尚无可靠证据基础，未进行凭相似标题合并。

## 实际证据样例

{cards_sample}
''',
'S51-全球宏观研究工作台验收.md':f'''# S51 全球宏观研究工作台验收

结论：现有真实结构化观测统一查询、历史曲线、原始证据与同口径冻结研究快照通过；BLS实际数值、完整官方历史vintage及行情验证仍缺失。

正式结构化宏观观测{counts['macro_observations']}条，历史版本{counts['macro_history_versions']}条，其中真实本地修订版本>1为{counts['true_macro_revisions']}，不把采集次数计为修订。FED、ECB和国家统计局实际公告通过同一研究卡片来源过滤查询，不能把仅有公告标题的来源伪装成已取得宏观数值序列。

指标目录来自实际macro_series；页面展示定义、来源、单位、频率、观测期、原始值、缺失值、本地版本、发布时间与采集时间、证据哈希及原始URL。曲线最多30个真实观测期，同日期选择最新本地版本，缺失值不补齐，横轴注明为观测顺序。观测和历史每页120条，避免全量加载。

沿用MacroResearchService的单位、频率、时点与证据哈希检查：不把ECB参考汇率当实时成交价，不把政策利率变化写成已证实政策意外；月度缺口不标为环比；非农总人数不变成新增非农；未知发布时间不做公告时点归因。冻结研究仅使用截止前实际获得、字段合格且合法留存的观测，真实市场预期、surprise和市场反馈保持未知。

本轮真实研究API查询及快照生成HTTP200。系统本地版本不是官方源端vintage，不能声称完整复原历史市场可得性。原始宏观/历史基线均保持不变，具体参见S54台账校验。
''',
'S52-全球金融每日简报验收.md':f'''# S52 全球金融每日简报验收

结论：结构化规则简报生成、过滤、复制文本、历史查询和旧版本保护通过；没有AI模型生成或对外发送。

简报覆盖上海时区所选日期至当天末尾或实际查询时间（取较早者）；拒绝未来日期。官方消息按真实发布时间筛选，聚合线索按首次发现时间筛选，宏观条目按实际采集范围筛选且明确不等同当日发布。已有研究快照仅在所选日期生成、证据截止不超查询时点时采用。

包含覆盖范围、官方发布、宏观变化、行业信息、风险、规则研究假设、待核线索、关注变量、源故障/缺口、证据目录十部分。来源、专题和用户关注实际约束输入。宏观专题在SQL分页前过滤，不让无关指标挤占输入；未找到专题资料显示暂无真实数据。个人规则冻结在输入中，之后修改关注不改变旧报告。

每次生成独立ID/快照ID、输入SHA-256、规则版本“金融证据规则简报-1.0”，AI版本为空。REPEATABLE_READ保证一次生成读取同一事务快照；数据库拒绝历史简报更新/删除；查询和详情限制同一身份，历史每页50条。十部分采用有界近期输入：官方20条、线索20条、宏观30条、研究快照10份，不声称全量总结。复制的是当前冻结报告，不发送邮件或外部服务。

真实生成样例：ID `{brief['id']}`，快照 `{brief['snapshot_id']}`，输入哈希 `{brief['input_hash']}`；重复生成新ID `{repeat['id']}`、新快照 `{repeat['snapshot_id']}`，旧版仍可查。两份输入一致时哈希可以相同，不把相同输入误写成新官方消息。

该实际简报中官方当日发布{len(brief['report']['official_events'])}条、采集范围内宏观{len(brief['report']['macro_observations'])}条、实际生成研究快照{len(brief['report']['supported_hypotheses'])}份、待核聚合线索{len(brief['report']['unverified_leads'])}条、引用证据{len(brief['report']['evidence_catalog'])}条。不能因为当天未取得新官方发布而补造新闻。FED半导体和空个人关注样例都为NO_MATCHING_DATA、证据0。

真实历史记录数量以financial_brief查询为准，备份快照包含{backup['restored']['brief_count']}份，后续浏览器验收可能追加新版本。
''',
'S53-持续运行与在线监控验收.md':f'''# S53 持续运行与在线监控验收

结论：保留既有观察及原始日志；不足24小时，仍待验收，未重置计时或拼接分段。真实官方LIVE双客户端仍0样本。

既有合格窗口起点 `{continuity['started_at']}`，本次报告读取累计{elapsed}秒（约{elapsed/3600:.2f}小时）、样本{len(continuity['samples'])}份、中断记录{interruptions}条。主服务PID {primary['after']['pid']}，创建时间{primary['after']['created']}，原JAR哈希 `{primary['after']['jarHash']}` 前后相同。原观察Node进程和双浏览器进程未停止，旧S46日志未删除。曾经存在的更早窗口及其失败仍保留，不能合并计时。

增量研究服务采用独立18083、新文件名JAR、关闭后台采集；升级的是新研究服务，主18080及原Web5173均未重启。新入口5176；原SSE与后台收集不依赖打开新页面。本次全库备份恢复约6分钟，原窗口仍有真实健康与资源采样；不能用短期无中断推断24小时完成。

观察原脚本继续记录健康、进程身份、CPU/内存、日志和业务库增长、数据库连接、任务状态、最近失败、Outbox审计积压及SSE客户端接收/渲染状态。此前ECB_DATA旧RUNNING已由既有CollectorGuard在真实重新取得会话锁后标记OWNER_LOST，具体旧任务 `{orphan['original']['id']}`，结束{orphan['original']['finished_at']}；本轮重新核查无RUNNING，因此没有额外发起历史采集或直接修改旧状态。

新增48迁移版本备份恢复到独立55432数据库 `{backup['restore_database']}`：原始证据{backup['restored']['evidence_count']}、Outbox{backup['restored']['outbox_count']}，哈希/资源哈希/ZIP哈希/引用错误0。备份文件SHA-256 `{backup['dump_sha256']}`，{backup['dump_bytes']}字节，正式库未恢复或覆盖；原始bytea已包含在一致备份，不重复导出大型文件。

隔离SSE首次复制配置存在端口/密钥不一致并产生超时，失败日志保留；修正隔离测试入口并独立重测后，双客户端自动渲染、离线补读去重、历史回填不提醒和253条分页保护通过。这些是隔离Fixture，不是实际官方新增验证。真实24小时来源持续调度、故障恢复及官方LIVE传播最终仍待足时审计。
''',
'S54-综合质量与真实性验收.md':f'''# S54 综合质量与真实性验收

结论：本轮代码、实际数据库增量、回归与备份恢复通过；全局正式业务闭环不能标为全部完成，BLS/SEC/LIVE/24小时阻塞仍存在。

既有121后端测试保留，新增6个解析测试、7个数据库测试，合计134通过、0跳过。旧5前端、旧10正式浏览器、新3正式研究浏览器、1隔离SSE分别通过。新增测试覆盖未知单位/脚注、月度与年均区分、明确官方日历、会计期间与数值比较、资源许可和预算、报告身份隔离/不可变/过滤/冻结关注、文件幂等/修订/事务回滚。失败阶段日志未改写为成功。

阶段前原始证据42140条、事件42134条、Outbox42134条均保留；宏观263条、历史119条不被重算覆盖；前45个Flyway文件校验码变更0。实际台账对比：

```json
{json.dumps(ledger['preserved'],ensure_ascii=False,indent=2)}
```

正式库逐字节重算原始SHA-256：{byte_audit['records']}条、错误{byte_audit['hash_errors']}；核查时间{byte_audit['checked_at']}。输入哈希基于生成时的UTF-8输入序列化，不从PostgreSQL重新排版的jsonb文本直接复算；证据逐个哈希与不可变报告版本分别验证。所有官方响应、GDELT ZIP SHA/MD5/长度、GLEIF HTTP与证据绑定、事实原文字段检查为0错误。正式Fixture0、来源绑定错误0、无事件证据0、Outbox重复0、GDELT越级事实0、无时间却核实的企业关系0；已核实企业关系0、独立事实支持0、AI实际执行0，没有虚构行情或模型事实。新资源响应哈希、日历引用、简报证据引用和虚构AI版本检查全部0。

备份恢复冻结快照重算全部原始bytea和ZIP哈希；恢复43千级真实记录，具体数以快照{backup['restored']['evidence_count']}为准，不与继续增长的线上计数拼接。新48迁移库及简报/日历一并恢复成功。原始证据、业务事实及简报均使用真实来源，观察未知状态不填假0/假发布时间。

浏览器截图：`docs/evidence/s54-practical-desktop.png`、`s54-practical-mobile.png`、`s54-isolated-sse.png`；原10项回归截图另存s54-regression前缀，未覆盖S46原截图。新品牌布局经移动端检查修正，页面无横向溢出。
'''}
for filename,body in specific.items():(root/'docs'/filename).write_text(body+'\n'+common,encoding='utf8')
previous=root/'docs/统一开发状态报告.md';preserved=root/'docs/evidence/s46-统一开发状态报告-保留.md'
if previous.exists() and not preserved.exists():preserved.write_bytes(previous.read_bytes())
summary='# 寰瞰 HUANKAN 统一开发状态报告 · S47–S54\n\n本阶段新增真实官方发布监控、金融研究卡片、宏观工作台和有证据冻结的每日规则简报；保留主采集服务及原连续观察。BLS诊断完成但实际访问仍403，SEC待配置，真实官方LIVE与24小时最终验收仍未完成。\n\n'
summary+='## 阶段状态\n\n|阶段|真实状态|\n|---|---|\n|S47|诊断与隔离适配器测试通过，正式BLS 0，访问恢复未通过|\n|S48|官方日历131与监控查询通过，真实官方新事件双浏览器待验收|\n|S49|SEC披露模型与预检准备，联系方式缺失，正式采集关闭|\n|S50|真实研究卡片、证据和规则假设展示通过；无行情验证|\n|S51|实际宏观观测、同口径研究和本地版本展示通过；源端vintage未知|\n|S52|过滤、快照、再生成、复制与历史保护通过；仅规则简报|\n|S53|原窗口持续观察中，未达到24小时|\n|S54|134后端、5前端、13正式浏览器、1隔离SSE及新备份恢复通过；外部阻塞未解除|\n\n'
summary+='## 交付报告\n\n'+'\n'.join(f'- [{f}]({f})' for f in specific)+'\n\n'+common
previous.write_text(summary,encoding='utf8');print('reports_written=9')
