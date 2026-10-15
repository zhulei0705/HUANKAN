from pathlib import Path
import json, hashlib, datetime, re
root=Path(__file__).resolve().parent.parent
def read(name):
    p=root/name
    return json.loads(p.read_text(encoding='utf-8-sig')) if p.exists() else {}
state=read('docs/evidence/s46-final-state.json'); details=read('docs/evidence/s46-acceptance-details.json'); units=read('docs/evidence/s46-count-units-final.json'); ledgers=read('docs/evidence/s46-ledger-integrity.json'); extra=read('docs/evidence/s46-new-ledgers.json'); restore=read('docs/evidence/s46-frozen-restore.json'); continuity=read('docs/evidence/s46-continuity-v4.json'); browser=read('docs/evidence/s46-online-browser-observation-v2.json')
now=datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(); counts=state.get('counts',{})
def log(name):
    p=root/'runtime'/name
    return p.read_text(encoding='utf8',errors='replace') if p.exists() else ''
backend='121项通过、0失败、0错误、0跳过' if 'Tests run: 121, Failures: 0, Errors: 0, Skipped: 0' in log('s46-backend-tests-v45.log') else '结果待核对'
formal='10项通过' if '10 passed' in log('s46-browser-final-retest.log') else '最终复测运行中；此前完整回归7项通过3项失败，失败日志保留'
isolated='1项通过' if '1 passed' in log('s46-isolated-browser-final45.log') else '最终回归运行中；此前单项复测通过'
changed=[]
for folder in ['backend/src','frontend/src','frontend/e2e','frontend/e2e-isolated','scripts']:
    for p in (root/folder).rglob('*'):
        if p.is_file() and datetime.datetime.fromtimestamp(p.stat().st_mtime)>=datetime.datetime(2026,10,10,14,57):
            changed.append({'file':p.relative_to(root).as_posix(),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()})
(root/'docs/evidence/s46-modified-files.json').write_text(json.dumps({'checked_at':now,'basis':'本轮起点14:57后的源文件修改时间清单；不等同Git差异','files':changed},ensure_ascii=False,indent=2),encoding='utf8')
common=f'''记录时间：{now}。数据库计数对应证据快照 {state.get('checked_at','待查询')}，后台采集仍在运行，不将快照写成永久固定数。

正式库：127.0.0.1:5432/huankan；独立测试实例：55432/huankan_test。凭证从本地环境变量读取，不写进本报告。正式 Flyway 当前 {units.get('migrations','待核对')} 次迁移；V1—V39 校验变化 {ledgers.get('changed_old_migrations','待核对')}。

本轮实际源文件清单及SHA-256：[s46-modified-files.json](evidence/s46-modified-files.json)。原始基线：[s39-preserved-ledgers.json](evidence/s39-preserved-ledgers.json)。最终计数：[s46-final-state.json](evidence/s46-final-state.json)。

回归：后端 {backend}；前端5项单元测试通过且生产构建成功；正式浏览器 {formal}；隔离双浏览器推送 {isolated}。命令：加载 scripts/load-local-env.ps1 后，用Java21执行 `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package`；在frontend执行 `npm.cmd test`、`npm.cmd run build`、`npx.cmd playwright test`；隔离浏览器使用 `scripts/start-isolated-web.ps1`、`npx.cmd playwright test -c playwright-isolated.config.ts`、`scripts/stop-isolated-web.ps1`。日志均保留在runtime/s46-*，最终后端日志为s46-backend-tests-v45.log。
'''
source_table='\n'.join(f"|{x['source_id']}|{x.get('http_status') if x.get('http_status') is not None else '任务未汇总HTTP状态'}|{x['status']}|{x.get('started_at')}|" for x in details.get('source_access',[]))
proof=state.get('fact_sample',{}); reason_rows=units.get('qualification',{}).get('body',{}).get('reasons',[])
reason_text='\n'.join(f"- {x['rejection_reason']}：{x['records']}条，资格为{x['qualified']}。" for x in reason_rows)
sample=details.get('research',{}).get('body',[])
snapshots='\n'.join(f"- {x['series_id']}：快照 `{x['id']}`，截止{x['as_of']}，实际冻结{len(x['inputs'])}条输入。" for x in sample)
remaining='BLS API真实403；核心CPI及PPI单位核验未完成；真实模型未配置；真实官方新增事件在线双端传播尚无合格样本；连续24小时观察未完成；SEC真实联系方式及关注公司、FRED密钥仍缺失；上市证券与真实业务关系数据不足。'
reports={
'S39-通知资格与采集口径验收.md':'''# S39 通知资格与采集口径验收

结论：完成资格解释、只读重算和事务计数审计；重大提醒仍为0，不强制制造资格。官方来源消息、历史回填、聚合线索与经过审核的重大提醒分别计量。

实际实现：V40、V44、V45；QualificationAuditService/Controller；GdeltCollector。notification_evaluation记录来源、事件类型、等级、规则版本、评估时间、资格、拒绝原因与投递状态；自动评估从部署游标向后读取，不把旧历史重新通知。只读重算可指定订阅，逐项核对范围、来源、优先级和用户关注，不能发送通知或写Outbox。过去的SENT不等于浏览器渲染，最终状态仍以接收与渲染账本为准。

资格拒绝原因（当前查询）：
'''+reason_text+'''

GDELT两次FAILED任务84e5fcd8-fac6-4f61-8e2a-0634ea3b67b3、4bf3da1f-16e0-4198-9a60-91702e851e62，声明新增与实际原始证据分别为466/466、992/992。原因是批次事务先提交、后续批次失败；保留失败状态和已经提交的证据。旧任务没有事务回执，不能补造当时批次数；新批次使用collector_batch_commit在同一事务记录实际fetched/inserted/duplicates。

额外核对发现ECB历史服务的new_count统计本地历史版本，不是原始证据行数。V45分开显示committed_raw_records与committed_history_versions，并记录count_unit，不修改旧任务的声明。最初7个疑似差异逐项核实为119个历史版本；修正口径后实际计数不一致为0。[历史差异初查](evidence/s39-counter-mismatches.json)、[最终计数单位](evidence/s46-count-units-final.json)。

性能：资格汇总曾实际耗时约9.97秒；新增job_id索引避免任务核对全表扫描，界面显示加载状态、重算最多100条。历史汇总仍不是低延迟查询，后续扩大规模应改为增量汇总并保留时点语义。
''',
'S40-BLS宏观数据验收.md':'''# S40 BLS宏观数据验收

结论：接入代码、隔离数据库验证与许可登记完成；正式API连通验收未通过。2026-10-10实际请求3个已核实序列，年份2025—2026，源站HTTP403，任务FAILED，入库0、重复0，无金融业务记录。403响应原文、SHA-256、请求体、时间、错误保存在bls_request，哈希错误0；本地管理接口返回200并不代表源站成功。失败后没有以网页数据替代，也没有自动重复请求。[真实请求](evidence/s40-bls-real-request.json)。

实际实现：V41；BlsCollector、BlsSourceAdapter、BlsController及6项解析测试。V1不需要Key；持久化预约与专用锁防止跨重启超额，保守滚动24小时25次、每次25个序列、10个年份。429/官方请求拒绝触发冷却；HTTP200仍必须检查REQUEST_SUCCEEDED、系列匹配与严格JSON。M13年均值不冒充月份，缺失值不改为0。

已核实配置：CUUR0000SA0为未季调CPI指数，1982—84=100；LNS14000000为季调失业率百分比；CES0000000001为季调非农就业总人数、千人，不是新增就业人数。CUSR0000SA0L1E与WPUFD4保留真实代码，但单位/完整定义未核实，禁用。单位和季调口径不互换。

保存原始观测、月份、原始值、脚注及初值/修订说明。API没有可验证的首次公告时间，published_at保持空值，观测日期仅表达月份。首次导入历史回填，不重大提醒。隔离测试验证同值重采不增加事件、脚注/值变化保留新旧版本、事务回滚及API业务失败门禁；这些结果不是BLS正式入库通过。

许可依据：[BLS官方条款](https://www.bls.gov/developers/termsOfService.htm)、[官方FAQ](https://www.bls.gov/developers/api_faqs.htm)、[官方序列定义](https://data.bls.gov/timeseries/CUUR0000SA0)。已登记检查时间、统计字段使用范围，页面标明获取日期、BLS来源和二次分析不由BLS保证，未使用BLS徽标。[许可记录](evidence/s40-license-review.json)。后续仅在官方访问恢复且预算许可时重试；本轮不能宣称BLS已真实入库。
''',
'S41-金融事实与宏观研究验收.md':'''# S41 金融事实与宏观研究验收

结论：沿用fact_candidate、fact_evidence、fact_review、fact_revision及冲突模型，增加不可变research_input_snapshot。1315个原子事实字段不是1315个独立事件；263条为字段一致性、1052条为来源检查，独立共证仍为0。

实际实现：V42；MacroResearchService/Controller；现有ECB与原子事实模型复用。研究只选择截止前已经取得的、许可有效且当前/截止时点审核合格的同序列观测版本。核对SHA-256、来源、单位、频率与实际值，最多冻结60条输入。所有输入保留证据ID、事实ID、版本、哈希、观测日期、可获得时间及原始字段；同一截止及规则可复算、不可改写。

真实正式样例：
'''+snapshots+'''

相邻观测可计算实际差值，百分比序列差值单位为百分点；分母为0不计算比例。月度缺期给出警示，不标成环比；同月前一年存在时才计算同比。预测值、市场一致预期、超预期幅度均保持未知。不把本地版本改成官方历史vintage，也不把采集时刻当首次发布时间。

隔离回归覆盖修订与回退版本、百分比/百分点、未来证据排除、旧审核时点与不可变快照。[真实研究输入及报告](evidence/s46-acceptance-details.json)。首次发布时间未知会限制公告时点研究，BLS真实研究仍受403阻塞。
''',
'S42-法人关系审核验收.md':'''# S42 法人关系审核验收

结论：真实2个法人、4份Level2证据对应2条待审关系；本轮自动审计2条，均为证据不足，人工确认0、已审核通过0。没有把会计并表变成供应链、客户、证券或持股比例事实。

实际实现：V42；RelationAssessmentService/Controller；原RelationReviewService继续承担人工证据审核。自动审计核对APPLE CANADA INC.与Apple Inc.两端LEI、模97校验、真实法律名称、CA-ON/US-CA、原始GLEIF节点、关系类型、ACTIVE状态、关系/会计/申报期间、登记有效时点、原始哈希和采集时间。登记记录的validFrom不是首次公开时间；ENTITY_SUPPLIED_ONLY不等于独立共证。

审计写入独立不可变relation_assessment，不改变原entity_relation状态。支持待审核、证据足够、证据不足、已确认、已拒绝、已失效等呈现，但不能无真实依据制造各状态实例。现有两条记录缺少可核实首次公开时间及独立支持，不能确认。自动核对成功不冒充人工审核。原始证据、拒绝理由与历史版本均保留。[实际关系审计](evidence/s46-acceptance-details.json)。

浏览器可查看自动审计规则、核对字段与限制。需要合法且明确披露的业务关系文件，才能进一步建立产业/真实业务链。
''',
'S43-金融事件研判验收.md':'''# S43 金融事件研判验收

结论：现有6份事件规则研判保留；新增3份真实ECB宏观研判报告，每份16个部分，关联不可变输入快照。没有生成行情、收益率或买卖指令。

实际实现：MacroResearchService/Controller、FinancialWorkbench.vue；复用既有ImpactService并修正有效事实审核状态引用。报告包含事件标题、事实摘要、官方来源、首次发布时间、关键数字、历史背景、发布机构、影响机制、可能关联市场、正/负条件假设、反面证据、数据缺口、观察变量、有效/失效条件和市场验证状态。规则版本为宏观同口径比较-1.0。

采用参考汇率、政策、通胀、就业规则；当前真实样例为ECB参考汇率。假设解释资金成本、购买力或外币收支传导，缺少真实企业敞口时不列确定受益/受损公司。首次发布时间未知、真实一致预期缺失、行情许可与反向证据不足均在报告显示。已有官方政策利率可查询，但本轮不把推断政策关联写成已验证因果。

市场验证状态全部待验证；不把相邻数字变化当成政策意外或价格反应。详情显示规则、截止时间、报告和可复算输入，桌面/移动端有界渲染。[报告及输入](evidence/s46-acceptance-details.json)。
''',
'S44-AI模型真实评测验收.md':'''# S44 AI模型真实评测验收

结论：真实模型配置缺失，真实AI执行0；质量、幻觉率、引文准确率、成本与耗时尚未验收。不能用规则模板充当AI结果。

实际实现：V43；AiEvaluationService/Controller；保留既有AiResearchService真实HTTP调用、失败记录、提示版本、输出校验和预算门禁。基于2份已入库官方宏观证据准备16条评测样本，8类任务为中文摘要、机构、宏观指标、金额单位、原文引述、正负影响、未知字段、非可信内容处理。样本持久化证据ID、哈希、原始预期字段、提示版本；准备样本不增加模型调用次数。

当前样本主要是宏观证据，event_id为空；现有按事件调用入口不能直接运行这批宏观样本。这是明确剩余接口工作，配置模型后仍须补齐宏观样本执行/评分与真实事件样本，不能宣称评测执行器已全面验收。没有构造攻击样本混入金融业务库；真实提示注入防护质量须用隔离评测输入测量。

不自动反复执行付费请求；失败原样保留，再次显式发起仍须预算通过。本地环境使用AI_ENABLED、AI_BASE_URL、AI_MODEL、AI_API_KEY、AI_MAX_REQUESTS_PER_DAY，默认禁用/预算0；不要把密钥贴入报告或仓库。页面显示未配置和真实执行0。[配置与样本状态](evidence/s46-acceptance-details.json)。
''',
'S45-关注名单与机会雷达验收.md':'''# S45 关注名单与机会雷达验收

结论：完成持久化关注、真实目录与规则过滤；正式关注记录保持0，不替用户植入测试偏好。浏览器展示真实目录，无符合规则消息时明确空状态。

实际实现：V43；WatchService/Controller；StreamService与ApiController在已有SSE上增量增加watchOnly；stream.ts、App.vue和FinancialWorkbench.vue。支持国家/区域、央行、指标、机构、法人公司、产业规则主题、事件类别、来源。只接受真实数据库目录里的目标，虚假公司/来源拒绝；LEI来自GLEIF记录，不推断上市证券或证券代码。产业主题是规则标签，不是企业产业归属事实。

保存/取消关注均写数据库，owner_key是当前访问身份的哈希；不暴露密钥。现有系统仍是访问令牌身份，不是新增多账号注册系统。隔离测试用不同身份检查不能跨用户删除或命中；重查可恢复设置，最低规则优先级与L2显式允许分别生效。L2默认不满足严格官方订阅，官方过滤与S39资格规则复用。

SSE在分页之前执行watch_matches；真实HTTP隔离测试先提交未关注ECB再提交关注FED，确认只收到FED位置。Last-Event-ID、断线补读、确认与去重保留原实现。候选每页50、补读每批100，页面最多有界显示，不加载全部历史。

法人关注目前不能证明真实业务披露，暂无对应事件时雷达为空。正式样例可使用已有ECB指标/央行来源；不强制触发重要消息提醒。[工作台手机截图](evidence/s46-workbench-mobile.png)、[桌面截图](evidence/s46-workbench-desktop.png)。
''',
'S46-综合可靠性与真实性验收.md':f'''# S46 综合可靠性与真实性验收

结论：完成代码与数据库回归、原始证据审计及隔离故障恢复；本轮不能整体标记全部通过。剩余：{remaining}

真实快照：原始证据{counts.get('raw_evidence')}，事件{counts.get('financial_events')}，Outbox{counts.get('outbox')}，事实字段{counts.get('atomic_facts')}，字段一致性{counts.get('field_checked')}，独立共证{counts.get('independent_supported')}，隔离记录累计{counts.get('quarantine')}。原始字节全量SHA-256检查{state.get('full_byte_audit',{}).get('records')}份，错误{state.get('full_byte_audit',{}).get('hash_errors')}；正式Fixture、来源绑定错误及Outbox重复均为0。基线证据39599、Outbox39593、宏观263、历史版本119和官方响应304均未改变。

本轮真实BLS抓取请求1次、HTTP403，成功入库0。GDELT新增由数据库快照计算；不能将全部历史增长等同本轮官方新增新闻。原始证据样例及官方链接见s46-final-state.json内fact_sample、legal_sample，研究结论完整输入见s46-acceptance-details.json。

官方连通记录：

|来源|真实任务HTTP|任务状态|开始时间|
|---|---|---|---|
{source_table}

FED的304是条件请求未修改，不是新的业务记录；GDELT任务的http_status为空时不填造200，实际下载状态应看批次及HTTP观测账本。BLS真实403单独阻塞。GLEIF的上述时间为既有本日真实请求，不冒充本轮新请求。

并行备份恢复期间，浏览器3项出现等待超时、隔离故障启动超过60秒、监控出现一次90秒以上采样间隔，均保留失败日志，未抹去或改成通过。正式后端在16:08:22记录连接重置与采集锁异常，随后连接恢复；稳定性因此仍未验收。恢复结束后重新进行独立复测。

数据库断开/恢复、Java停止/重启在55432独立schema与专用代理完成，没有停止共享5432。结果见[s46-final-isolated-runtime-faults-retest.json](evidence/s46-final-isolated-runtime-faults-retest.json)。隔离双浏览器测试验证自动渲染、LIVE确认、断线补读、去重及历史回填不提醒；这些是隔离样本，不替代正式官方新增验收。正式Web回归含桌面/手机、事实、证据、来源与未配置状态。

备份第一轮：pg_dump和实时证据导出取得不同快照，检查拒绝通过，保留runtime/s46-backup-restore.log。后续以pg_dump的同一冻结快照在独立恢复库校验/导出，不修改正式库。冻结恢复状态：{'已通过' if restore.get('restore_passed') else '执行中，尚未标记通过'}；完整结果见[s46-frozen-restore.json](evidence/s46-frozen-restore.json)。此备份保留备份时的迁移版本，以恢复结果记录为准，后续新增迁移仍由Flyway执行。

连续24小时：当前唯一验收窗口为[s46-continuity-v4.json](evidence/s46-continuity-v4.json)，开始{continuity.get('started_at')}，截至本报告实际采样{continuity.get('observed_seconds',0)}秒，状态观察中、未完成。早期日志因部署或监控改进中断，原样保留，不拼接。记录进程身份、健康、数据库连接、内存、CPU、审计游标积压、任务失败、数据库增长、SSE及运行日志/浅层存储；不每分钟遍历冻结备份的数万文件。

在线双浏览器独立窗口：[s46-online-browser-observation-v2.json](evidence/s46-online-browser-observation-v2.json)，开始{browser.get('started_at')}。截至报告真实官方新增双端样本仍为0，待验收。两个浏览器在线不等于完成真实新事件传播。

真实结论：BLS、AI质量、连续24小时与正式官方新增在线验收不满足通过条件，其余已经运行的测试按具体日志记录。
'''
}
for name,body in reports.items():
    (root/'docs'/name).write_text(body+'\n## 实际环境与验证记录\n\n'+common,encoding='utf8')
previous=root/'docs/统一开发状态报告.md';history=root/'docs/统一开发状态报告-S38历史快照.md'
if not history.exists():history.write_bytes(previous.read_bytes())
links='\n'.join(f'- [{name}]({name})' for name in reports)
summary=f'''# 寰瞰 HUANKAN 统一开发状态报告

最新阶段：S39—S46；记录时间{now}。新增正式通知资格/任务口径审计、BLS接入门禁、宏观研究快照、法人关系自动审计和持久化关注工作台；不是全阶段全部通过。

后端{backend}；正式浏览器{formal}；隔离双浏览器{isolated}；前端5项与生产构建通过。当前正式库迁移{units.get('migrations')}；真实证据{counts.get('raw_evidence')}、事件/Outbox{counts.get('outbox')}、事实字段1315、字段一致性263、独立共证0、待审关系2、通过关系0、规则事件报告6、宏观研究快照3、AI真实执行0。新增关注规则隔离测试通过，正式关注不预填。

BLS本次真实HTTP403、业务入库0；3个已核实序列已配置、核心CPI/PPI未核实定义保持禁用。两条GLEIF关系自动审计证据不足，人工确认0。16条AI样本只是准备，不是模型评测结果。

重大提醒资格0有逐条拒绝原因；已提交GDELT失败任务保留466、992条真实证据。ECB历史新增以版本为单位，不能误算原始证据；修正后计数不一致0。旧39次迁移与39599条基线原始证据完整保留。

{remaining}

连续观察以s46-continuity-v4.json及s46-online-browser-observation-v2.json的单一窗口为准；未满24小时不能通过，旧中断日志不合并。备份冻结恢复{'已通过' if restore.get('restore_passed') else '执行中，未宣称通过'}，详情见S46报告。

## 本轮交付

{links}

此前S31—S38状态保存在[统一开发状态报告-S38历史快照.md](统一开发状态报告-S38历史快照.md)，此前各阶段报告继续保留。

{common}
'''
previous.write_text(summary,encoding='utf8');print('已根据实际证据更新8份阶段报告和统一报告；未满足项保留未验收。')
