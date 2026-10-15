# S55 全站中文化与 UI 体验优化验收

验收日期：2026-10-12（北京时间）。本报告替换此前只覆盖研究六页的旧结论，以本次实际源码、运行网页、查询和测试为准。

## 原始问题与增量实施

原阶段页面默认展示外文标题，来源健康和事件混排，辅助字号低至10px，时间和后台状态容易混淆。现有项目保持 Vue 3 + TypeScript，没有重建系统。

主应用 index.html 与 s54.html、terminal.html 现在共用 App.vue、Vue Router、组件和全局主题。s54.html 保留作为兼容阶段入口；terminal.html 可作为产品入口。正式13项导航按现有架构排列；产业情报、风险预警明确“未接入”且菜单禁用。每日简报、事实及审核工具另归研究工具区。SEC准备归系统设置，官方可用性归数据源监控。组件中旧主站外链仅在非嵌入模式存在，当前主应用使用嵌入模式，不产生两套可见导航。

## 实际修改和核查文件

- frontend/src/App.vue：共同导航、路由同步、中文机构和可信标题、原文展开、核验状态及移动菜单；修复模板多余引号。
- frontend/src/router.ts、main.ts、s54-main.ts：主入口与阶段入口共用路由及主题。
- frontend/src/presentation/zh.ts：机构、可信译文、规范指标、北京时间、原始UTC、时长和HTTP状态；HTTP错误不能被历史成功状态覆盖。
- frontend/src/terminal.css：字体与主题变量、响应式卡片、异常文字、对齐控件、键盘焦点和触摸尺寸。
- frontend/src/components/OfficialMonitor.vue：五部分结构、独立查询和SSE状态、来源健康、真实统计、数据库分页筛选、原文和技术证据展开。
- frontend/uiProjection.ts、vite.config.ts、vite.s54.config.ts：既有本地真实数据库只读展示投影与产品代理。分页50条，字段白名单、北京时间日界、只读事务及查询超时；不修改证据或采集规则。
- frontend/src/components/PracticalDesk.vue：简报title变量遮蔽修复、查询接口状态、HTTP中文说明、日期重置与原文。
- frontend/src/components/ResearchDesk.vue、FinancialWorkbench.vue、FactEngineDesk.vue：中文机构、指标、翻译资格和未知官方发布时间。
- frontend/src/components/BrowserObservation.vue、MarketLinkage.vue、MacroHistory.vue：北京时间与UTC核对、传播/披露/版本时间分离。
- frontend/src/components/AiAnalysis.vue：只有成功且有实际执行标识的结果显示译文或摘要，明确机器翻译待审核。
- frontend/src/components/DateField.vue：中文星期、年月、今天/清空/关闭及北京时间选择，不依赖英文原生日历。
- frontend/src/tests/presentation.test.ts、ui-query.test.ts、stream.test.ts；frontend/e2e-s54/s55-ui.spec.ts、s55-unification.spec.ts、s55-mobile-all.spec.ts、practical.spec.ts：语义与浏览器验收。

以上列出本次交付核查和修改范围；仓库当前文件均未跟踪，不能据git diff虚构与上一次提交的精确增量。

## 字体、主题与中文化

页面标题24px，手机22px；模块19px；正文16px；表格15px；标签14px；辅助至少13px。中文系统无衬线字体，行高1.6，按钮/输入至少42px，手机按钮44px；卡片圆角10px。异常同时使用中文文字、边框和背景。实测字号及对比度保存于 evidence/s55-display-audit.json；普通正文和辅助角色超过AA的4.5:1。不是对任意内容和状态的全站WCAG认证。

已核查所有19条产品路由的导航、标题、按钮、筛选、分页、状态和浏览器标题；未接入模块仍明确缺失。九家登记机构统一中文名称。英文原文、URL、哈希、序列ID和技术配置名保留供核对；技术缩写解释可在页底展开。当前复制简报没有文件导出功能，未虚构导出文件名能力。

标题按人工核验、实际成功执行的机器译文、精确注册指标映射排序。无可信译文显示“中文翻译待完成”；官方中文原文保留。机器翻译和分析失败不会补造标题/摘要。原文、官方URL与证据入口继续可查。

## 实际数据与时间

只读盘点：2026-10-12 09:03:48 北京时间，官方事件1313，国家统计局原文1000，ECB_DATA指标263，FED/ECB等外文待译50。翻译状态全部NOT_CONFIGURED，没有机器翻译执行，不能把中文指标映射算作AI翻译。证据：evidence/s55-translation-inventory-current.json。持续采集可能改变后续计数。

全部显示北京时间，可展开或悬停核对UTC。官方发布时间、首次发现、采集、入库、来源检查、SSE发送和客户端接收分开。缺失官方发布时间显示未知；原站时区接口未提供时不猜测。历史回填只能显示历史发现延迟，不等同实时传播延迟。长秒数转为中文时长。ECB参考汇率按完整序列解释币种和“1欧元对应所示币种数量”，标注日度参考，不称实时成交价。

## 页面结构与真实状态

官方监控分为服务时间/状态、真实统计、可折叠筛选、来源健康、分页事件。技术证据放展开区。BLS明确访问被拒绝HTTP403，304内容未修改；SEC/FRED未启用显示待配置。后台顶部报告启用来源和运行任务数，全局采集开关接口未提供，明确保留这一限制。通知数仅为本页实际合格LIVE通知，不冒充全数据库新增量。

筛选由数据库只读投影执行，覆盖完整官方事件范围而非当前页；默认不限定日期，日期按北京时间日界。事件50条分页、既有虚拟滚动保留、消息批量处理和刷新合并。宏观曲线最多30期，不全量下载历史GDELT。模块无数据、查询失败、加载中分别展示，不以失败伪装0。

## 实际截图

截图来自运行网页，不是设计图。截图时间由本次浏览器执行记录和截图旁的页面查询时间核对；时间戳UTC可加8小时转北京时间。采集会继续更新，截图不是固定业务基线。

- [桌面1920×1080](evidence/s55-desktop-1920.png)
- [桌面1366×768](evidence/s55-desktop-1366.png)
- [手机390×844](evidence/s55-mobile-390.png)
- [手机宏观指标与真实报价口径](evidence/s55-macro-mobile.png)

## 测试结果

最终结果在本报告末尾记录。原始日志：runtime/s55-backend-current.log、runtime/s55-browser-current.log、runtime/s55-sse-current.log。展示证据：evidence/s55-display-audit.json、s55-all-mobile-audit.json。

## 服务保护与未解决问题

没有部署或重启正式后端。主采集进程16904，创建时间2026-10-10 15:59:54北京时间，本轮仍相同，见evidence/s55-primary-current.json。单独启动的18084仅连接55432测试库，关闭采集；SSE测试结束清理其测试schema，不操作正式记录。既有浏览器回归会通过产品接口新增规则简报或测试审核记录，保留历史，不删证据；这不改变采集规则。

BLS访问仍403；SEC联系方式/FRED配置未就绪；50条外文无真实译文；行情授权和AI执行未接入；全局采集开关/原站时区接口不完整；真实官方LIVE双浏览器和24小时观察结论需独立持续观察证据，本次UI测试不能替代。前端开发服务器热更新可能刷新现有页面，不据此声称原浏览器会话完全未变；正式后端采集进程未重启。

## 最终验收记录（2026-10-12）

- 后端：mvn test，134通过，0失败/错误/跳过，09:08:37完成；使用独立测试库，不打包替换运行中JAR。
- 前端：16项Vitest通过；vue-tsc、主入口build及vite.s54.config.ts双入口构建通过（09:12最终构建）。
- 完整浏览器首次运行22项：19通过、3失败，日志s55-browser-current.log。失败分别为查询状态旧断言、官方历史版本缺失文案、BLS/AI按钮英文代码旧选择器。已修复状态断言、恢复“尚未证实”语义、更新中文按钮选择器。
- 修复复测9项全通过：包含全部3个失败用例、相关功能复测及新增手机全路由用例，日志s55-browser-repaired.log。合并覆盖23个不同浏览器用例，均有最终通过记录；未伪称首次全量运行一次全绿。
- 隔离SSE1项通过：事务提交双客户端更新、离线补读去重、历史回填不提醒；日志s55-sse-current.log。只清理55432独立测试schema。
- 19个路由390px审计：中文标题、业务文案至少13px、无横向溢出、无页面运行异常，见s55-all-mobile-audit.json；包括两个明确未接入模块的空能力页面。
- 桌面字号24/16/13px；实测正文15.91:1、辅助9.11:1、来源卡14.24:1；桌面专项控制台错误0。见s55-display-audit.json。
- 实际截图：1920与1366于09:09:55、390手机于09:09:57、宏观手机09:09:58北京时间拍摄。查询时刻保留在截图界面；元数据s55-screenshot-times.json。

|要求|证据或用例|
|---|---|
|全站中文、来源中文名称|s55-unification、s55-mobile-all、presentation.test|
|原文保留、翻译资格|s55-ui原文展开；presentation译文执行ID与人工核验测试|
|时间一致、HTTP翻译、异常来源|presentation时间/HTTP；s55-ui BLS403与304；s30历史版本|
|筛选、重置与日期范围|s55-ui、s55-unification完整数据库分页与北京时间日界|
|桌面、390px、控制台、溢出、深色对比度|s55-ui、s55-mobile-all、实际截图及display-audit|
|SSE重连及增量列表|s55-ui断线重连；隔离push双客户端/补读/去重|
|真实证据入口、无数据空状态|practical真实卡片链接、s55-ui未来日期为空、multi-source证据|

所有验收中的隔离Fixture仅用于独立测试库，不是正式产品新闻或截图数据。前端没有执行翻译、制造行情或补造来源状态。本次未改后端业务源码、正式采集配置、迁移、原始证据或历史版本。
