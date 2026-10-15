# S03 GDELT增量发现验收

更新：2026-10-09T05:57:13.906Z。数据来自本机真实PostgreSQL和真实来源；不宣布完整V4.0交付。

依据[官方数据说明](https://www.gdeltproject.org/data.html)、[GDELT2.0增量文档](https://blog.gdeltproject.org/gdelt-2-0-our-global-world-in-realtime/)及[GKG2.1代码本](https://data.gdeltproject.org/documentation/GDELT-Global_Knowledge_Graph_Codebook-V2.1.pdf)，选择 https://data.gdeltproject.org/gdeltv2/lastupdate.txt 的15分钟GKG批次，不下载全历史。许可登记仅为本地开放GKG元数据研究，第三方原文版权不随索引获得，不下载或再发布全文。

- 批次20261009040000：真实文件已保存，3260698字节，760行，保留310，排除450，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009040000.gkg.csv.zip，SHA-256 576a234d08f12f72ca45951989e2ad45495467a0d38040741ae819f0664b6020。
- 批次20261009041500：真实文件已保存，3467319字节，820行，保留290，排除530，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009041500.gkg.csv.zip，SHA-256 2119eb0fb60f2d01595efb606a9536d8479789d0a2ceb72a63115c685e657421。
- 批次20261009043000：真实文件已保存，3568434字节，817行，保留301，排除516，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009043000.gkg.csv.zip，SHA-256 c600fa678902b3f55496092541580406167bbb076f95b0017263c5b0721edd66。
- 批次20261009044500：真实文件已保存，3355626字节，769行，保留260，排除509，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009044500.gkg.csv.zip，SHA-256 600baa5c1dcb98ab0040d78d8348eb51c4c61e6731202d5af0bea834136cd376。
- 批次20261009050000：真实文件已保存，2952422字节，696行，保留256，排除440，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009050000.gkg.csv.zip，SHA-256 70ddd647104fe00b6535ec5e452e792c2176c3257e0cf33b8243ca39fff44e83。
- 批次20261009053000：真实文件已保存，3264729字节，738行，保留280，排除458，状态SUCCESS，文件https://data.gdeltproject.org/gdeltv2/20261009053000.gkg.csv.zip，SHA-256 2aa060324a4d0bf0550fcc809a7d134cbaac1de96831b7ac104cb538300ca6d5。
- 批次20261009060000：实际失败，IOException: GDELT_HTTP_404；清单声明4357290字节（不是成功下载字节数），未产生业务事件；文件https://data.gdeltproject.org/gdeltv2/20261009060000.gkg.csv.zip，SHA-256未取得，验收未通过，等待后台重试。

成功批次的清单MD5与实际下载匹配，并保存SHA-256与完整ZIP；数据库约束字节数与两个摘要。下载上限10MiB、解压上限80MiB、100000行、单行1MiB，严格27列、UTF-8、唯一安全ZIP条目；不解压到任意文件路径。HTTPS升级只允许data.gdeltproject.org指定路径，禁止重定向。

文件.part保存真实断点，Range响应验证Content-Range，全部已下载的文件在进程恢复后直接重新校验；429持久化冷却。金融线索每次最多200条，以事务提交的completed_lines继续。第二批在应用重启后继续完成；真实下载没有中途中断，网络续传仅在隔离传输故障测试证明，不能写成真实外部网络中断验收。

过滤经济、央行、银行/监管、贸易、能源、半导体、AI及大型企业等主题或机构索引。排除数是非匹配主题或不安全链接，不是虚构的数据质量失败。GKG不提供可信新闻标题/发布时间，界面明确显示主题索引与发布时间未知；批次处理时间单独显示，语言未确认则und，全部L2/PENDING。每条证据保存实际解压行字节与哈希、批次依赖、新闻URL、主题、机构和FIPS国家位置代码。原文链接不代表取得原文内容再分发权或事实核验。

精确原文URL与已核实官方证据形成待核验候选关联，实际候选0条，没有捏造匹配或把聚合线索升级VERIFIED。相同批次跳过处理，重复批次和重复记录分别计数。

修改：V7–V10迁移、GdeltParser/GdeltDownloader/GdeltCollector、EvidenceService、CollectorService、QueryService、scripts/sql/gdelt-license-local.sql、ExpansionTest/GdeltResumeTest/DatabaseGateTest、App.vue。

## 实际数据库与来源

快照：2026-10-09T13:57:11.674109+08:00。复用 127.0.0.1:5432/huankan，Flyway V1–V10 均成功；没有重建数据库，未修改其他项目的数据库或8080服务。测试使用55432/huankan_test内独立schema。

|来源|等级|唯一事件|证据版本|已核实来源证据|Outbox|真实官方新增样本|
|---|---|---:|---:|---:|---:|---:|
|ECB|L0|29|29|29|29|0|
|FED|L0|20|20|20|20|0|
|FRED|L0|0|0|0|0|0|
|GDELT|L2|1697|1697|0|1697|0|
|SEC|L0|0|0|0|0|0|

总事件 1746、证据 1746、Outbox 1746；哈希错误 0、正式库Fixture 0。隔离表 4 条是失败采集批次记录，不代表存在同数量的伪造金融记录；真实原因见下方异常说明。核实状态指来源、哈希及原始时间校验，不保证官方公告中的全部经济结论。

本轮统计起点为首条真实HTTP观察记录，计数来自数据库；new_count是新增证据版本，不能直接当作刚刚发布的新闻数量。GDELT去重记录与去重批次使用不同字段。

|来源|任务|RSS解析/金融线索选取|新增证据版本|重复记录|重复批次|失败任务|隔离批次|
|---|---:|---:|---:|---:|---:|---:|---:|
|ECB|27|750|29|721|0|2|2|
|FED|25|20|0|20|0|2|2|
|GDELT|16|1697|1697|0|2|2|0|

- FED：事件 `6da89c5a-05d5-469f-bf8e-5feafd24cdd6`，证据 `4e33ff75-dcdb-4fd1-b101-ecedb3ad9fd0`，原始来源 https://www.federalreserve.gov/newsevents/pressreleases/enforcement20261008a.htm，14722 字节，SHA-256 `98ec882b93863aa2af3f928486c86db4cf2a0f2527252cf37fedcd9a81298672`，下载重算一致。
- ECB：事件 `89127147-2af4-4fc9-9f81-cf378a6b44cd`，证据 `8eb3be7a-8523-451b-9bce-fac7559d91c2`，原始来源 https://www.ecb.europa.eu//press/accounts/2026/html/ecb.mg261008~a10153d090.en.html，5790 字节，SHA-256 `4bd9e1b1553a58d1f0fe7f3723ff57ac8e4b2c0395538d59ae525110d16c3a9d`，下载重算一致。官方原文实际 HTTP 200。
- GDELT：事件 `0030e4c2-404c-4927-8a3e-2f881bd9da37`，证据 `4bda298e-cdaf-4cc6-aa7c-3deae3921b7c`，原始来源 https://data.gdeltproject.org/gdeltv2/20261009040000.gkg.csv.zip，11746 字节，SHA-256 `6388a28dd74e1154c0a58c07bf734278e47ca2b673fb53c46c0d2b831e6f9894`，下载重算一致。

原始JSON：[实际状态](evidence/s-stage-actual-state.json)，包含任务、逐次请求状态/耗时/ETag/Last-Modified、许可依据、批次和样例。

|来源|最后实际HTTP|最后请求毫秒|平均请求毫秒|请求成功率|最近任务总毫秒|
|---|---|---:|---:|---:|---:|
|ECB|200|233|536.5|96.2%|759|
|FED|304|856|882.2|92.3%|869|
|FRED|无响应/未请求|未知|无样本|无样本|未运行|
|GDELT|404|171|1090.4|88.2%|1192|
|SEC|无响应/未请求|未知|无样本|无样本|未运行|

## 执行命令与结果

先在PowerShell加载 `. ./scripts/load-local-env.ps1`，设置JAVA_HOME为 `C:\Program Files\Java\jdk-21.0.10` 并加入PATH；凭证不写入源码、报告或命令输出。

- `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package -B`：39通过，失败0、错误0、跳过0；构建日志 [后端](evidence/s-stage-backend.log)。Windows打包前仅停止经过JAR路径和创建时间核对的本项目进程。
- `npm.cmd --prefix frontend test`：5通过；`npm.cmd --prefix frontend run build`：TypeScript与Vite通过。
- `npx.cmd --prefix frontend playwright test -c frontend/playwright.config.ts`：4通过，包含原3项正式Web测试及新增多来源/虚拟滚动验收。
- `scripts/start-isolated-web.ps1` → `npx.cmd --prefix frontend playwright test -c frontend/playwright-isolated.config.ts` → `scripts/stop-isolated-web.ps1`：1通过，独立schema已清理。原有4项浏览器测试均在此次共5项中回归。
- `node scripts/check-live-stream.mjs`：真实历史两个客户端各1746条，唯一1746，游标873补读873，NEW_EVENT 0。这是历史回放，不是官方新公告推送延迟验证。
- `scripts/check-persistence.ps1`：1746份证据，重启前后ID与哈希清单一致，健康UP。重启的是寰瞰应用，未重启其他项目共用的PostgreSQL服务。

日志：[前端](evidence/s-stage-frontend-tests.log)、[构建](evidence/s-stage-frontend-build.log)、[正式浏览器](evidence/s-real-browser.log)、[隔离浏览器](evidence/s-isolated-browser.log)、[SSE回放](evidence/real-sse-replay.json)、[持久化](evidence/persistence-restart.json)。

## 实际异常与剩余限制

美联储曾出现TLS握手超时与DNS解析/传输初始化失败，各失败任务均未生成金融事件，后续真实HTTP200恢复并正确去重；失败记录保留。异常总数以快照查询为准。网络失败没有响应状态，记录NULL，不能伪记HTTP200。SSE发送失败实测30次，显式客户端确认143条；关闭/断线客户端的实际发送失败计入全局指标，不声称全部为服务器故障。

- ECB 最近异常：2026-10-09T05:50:45.466+00:00，Failed to resolve 'www.ecb.europa.eu', couldn't setup transport: [id: 0x09e833b3]；快照时最后HTTP 200，最近任务SUCCESS。
- FED 最近异常：2026-10-09T04:15:07.580+00:00，Failed to resolve 'www.federalreserve.gov', couldn't setup transport: [id: 0x48a4b82e]；快照时最后HTTP 304，最近任务SUCCESS。
- GDELT 最近异常：2026-10-09T05:53:06.513+00:00，IOException: GDELT_HTTP_404；快照时最后HTTP 404，最近任务FAILED。

GDELT当前失败批次1个：20261009060000 IOException: GDELT_HTTP_404。失败批次没有被标记下载成功或通过校验，后台保留批次并按配置重试。此前已完成6个批次；不能据此前成功记录宣称最新批次也已通过。

尚无真实新增事件样本。真实修订、官方新增发布到浏览器延迟、所有告警场景的正式触发、长时间压力/断网、数据库服务重启及备份恢复尚未完整验收。GDELT支持已登记批次和下载断点恢复；长时间停机遗漏的中间批次尚未实现自动历史补齐。SEC真实邮箱为空，自动采集关闭，官方映射与正式申报连通未验收；FRED未接入。Windows开机自动启动与多实例限流仍未完成。
