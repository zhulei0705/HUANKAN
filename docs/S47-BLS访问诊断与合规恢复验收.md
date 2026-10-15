# S47 BLS访问诊断与合规恢复验收

结论：合规诊断与关闭失败入库已验证；实际访问恢复未通过。6次有限请求均HTTP403，新增正式BLS观测0，重复导入新增0，未将失败响应生成新闻或数值。失败响应作为HTTP诊断留存，不计为成功业务记录。

|方法|实际路径|HTTP|实际字节|实际响应SHA-256|
|---|---|---|---|---|
|POST|https://api.bls.gov/publicAPI/v1/timeseries/data/|403|408|4a4be1e640b084b352288669165c266ed05fe0802b5d04708d89f3cfadb65069|
|GET|https://api.bls.gov/publicAPI/v1/timeseries/data/CUUR0000SA0|403|419|0a415706ee003189e0685cae3e383025915f449d7f98984a564f5e0cee9b2daa|
|GET|https://download.bls.gov/pub/time.series/cu/|403|405|bcbaca7fb079c718b49786d920fb1d036d9430f555414f1ad24662e511872919|
|GET|https://download.bls.gov/pub/time.series/cu/cu.data.1.AllItems|403|435|2d90b5b425a162623c31cf7abb84fa2d1c9ab63585ae6d3efbabf8a8d71fc132|
|GET|https://download.bls.gov/pub/time.series/cu/cu.footnote|403|420|4a912731a8282e5ce5fe7dca466f1987b0be9a7f0e84a0c78ac483350a3e1dfa|
|GET|https://download.bls.gov/pub/time.series/cu/cu.series|403|418|d76656f56f896f79e45f7a5d5eba837aa015e3400360f2fa91a0f228b8862ee4|

依据：[BLS官方API签名](https://www.bls.gov/developers/api_signature.htm)、[BLS官方开发者条款](https://www.bls.gov/developers/termsOfService.htm)、[BLS官方CPI文件目录](https://download.bls.gov/pub/time.series/cu/)。核查日期2026-10-10；资源使用范围、依据和检查时间已登记 `official_resource_policy`。仅留存原始公共统计响应、系列定义、脚注及数值，不使用徽标，不宣称背书，不改值冒充官方结果。

官方签名核对后，POST使用JSON `seriesid/startyear/endyear`，单系列GET使用明确官方路径。两个方法和公开文件都403，DNS解析与TLS握手有真实结果；不能据此断定IP封锁、地理封锁或特定防火墙根因。没有旋转UA、代理绕行或重复强攻。同URL24小时内只允许一次显式诊断，跨实例持久预约；正文最大4MiB/元数据2MiB/错误64KiB，并使用实际正文读取截止时间。

文件适配器仅接受有证据的CUUR0000SA0全项目未季调、月度、1982-84=100；拒绝未知脚注、重复月份和不匹配单位，M13年度平均不混入月度。精确TSV行、数值、脚注与系列/文件响应关联，哈希基于真实文件字节。每个同期修订保留新旧证据和事实版本；未知首次发布时间保留空，不把Last-Modified或采集时间变为发布时间。

隔离库已验证导入、重复0新增、修订保留以及事实提交失败时证据/事件/Outbox整体回滚。正式文件未能取得200，因此不宣称真实BLS文件导入通过。

### POST https://api.bls.gov/publicAPI/v1/timeseries/data/

请求时间：2026-10-10T17:18:10.342026+08:00；响应时间：2026-10-10T17:18:11.561187+08:00。

请求配置：`{"url": "https://api.bls.gov/publicAPI/v1/timeseries/data/", "body": "{\"seriesid\":[\"CUUR0000SA0\"],\"startyear\":\"2025\",\"endyear\":\"2026\"}", "accept": "application/json", "method": "POST", "trace_id": "86306bb3-0de3-4873-9c3b-f3637b4d5fa6", "redirects": "禁止", "size_limit": 4194304, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "application/json", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:11 GMT", "content-type": "text/html", "content-length": "408"}`

网络：`{"dns": ["23.35.148.176", "23.35.148.189"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;api&#46;bls&#46;gov&#47;publicAPI&#47;v1&#47;timeseries&#47;data&#47;" on this server.<P>
Reference&#32;&#35;18&#46;3def2117&#46;1791623891&#46;61d2fed
<P>https&#5
```

### GET https://api.bls.gov/publicAPI/v1/timeseries/data/CUUR0000SA0

请求时间：2026-10-10T17:18:11.579713+08:00；响应时间：2026-10-10T17:18:12.064792+08:00。

请求配置：`{"url": "https://api.bls.gov/publicAPI/v1/timeseries/data/CUUR0000SA0", "body": "", "accept": "application/json", "method": "GET", "trace_id": "19d4ebb2-c407-4b5e-987c-85eabba7cc1f", "redirects": "禁止", "size_limit": 4194304, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "未发送", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:11 GMT", "content-type": "text/html", "content-length": "419"}`

网络：`{"dns": ["23.35.148.176", "23.35.148.189"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;api&#46;bls&#46;gov&#47;publicAPI&#47;v1&#47;timeseries&#47;data&#47;CUUR0000SA0" on this server.<P>
Reference&#32;&#35;18&#46;30ef2117&#46;1791623891&#46;180c886

```

### GET https://download.bls.gov/pub/time.series/cu/

请求时间：2026-10-10T17:18:12.084742+08:00；响应时间：2026-10-10T17:18:13.18926+08:00。

请求配置：`{"url": "https://download.bls.gov/pub/time.series/cu/", "body": "", "accept": "text/plain,text/html;q=0.9", "method": "GET", "trace_id": "c22db381-2243-48ec-834b-617e66d23d30", "redirects": "禁止", "size_limit": 65536, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "未发送", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:13 GMT", "server": "AkamaiGHost", "content-type": "text/html", "content-length": "405"}`

网络：`{"dns": ["23.50.28.98"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;download&#46;bls&#46;gov&#47;pub&#47;time&#46;series&#47;cu&#47;" on this server.<P>
Reference&#32;&#35;18&#46;d0bd2817&#46;1791623893&#46;b78aa833
<P>https&#58;&#
```

### GET https://download.bls.gov/pub/time.series/cu/cu.data.1.AllItems

请求时间：2026-10-10T17:18:13.3082+08:00；响应时间：2026-10-10T17:18:13.666858+08:00。

请求配置：`{"url": "https://download.bls.gov/pub/time.series/cu/cu.data.1.AllItems", "body": "", "accept": "text/plain,text/html;q=0.9", "method": "GET", "trace_id": "81366eaf-e098-451e-8e08-ef52f01a9267", "redirects": "禁止", "size_limit": 4194304, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "未发送", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:13 GMT", "server": "AkamaiGHost", "content-type": "text/html", "content-length": "435"}`

网络：`{"dns": ["23.50.28.98"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;download&#46;bls&#46;gov&#47;pub&#47;time&#46;series&#47;cu&#47;cu&#46;data&#46;1&#46;AllItems" on this server.<P>
Reference&#32;&#35;18&#46;d0bd2817&#46;179162389
```

### GET https://download.bls.gov/pub/time.series/cu/cu.footnote

请求时间：2026-10-10T17:18:13.668946+08:00；响应时间：2026-10-10T17:18:14.086309+08:00。

请求配置：`{"url": "https://download.bls.gov/pub/time.series/cu/cu.footnote", "body": "", "accept": "text/plain,text/html;q=0.9", "method": "GET", "trace_id": "54818678-731b-4dda-8b18-0f263a56f199", "redirects": "禁止", "size_limit": 65536, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "未发送", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:14 GMT", "server": "AkamaiGHost", "content-type": "text/html", "content-length": "420"}`

网络：`{"dns": ["23.50.28.98"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;download&#46;bls&#46;gov&#47;pub&#47;time&#46;series&#47;cu&#47;cu&#46;footnote" on this server.<P>
Reference&#32;&#35;18&#46;d0bd2817&#46;1791623894&#46;b78ab3b1

```

### GET https://download.bls.gov/pub/time.series/cu/cu.series

请求时间：2026-10-10T17:18:14.134236+08:00；响应时间：2026-10-10T17:18:14.62508+08:00。

请求配置：`{"url": "https://download.bls.gov/pub/time.series/cu/cu.series", "body": "", "accept": "text/plain,text/html;q=0.9", "method": "GET", "trace_id": "39ea0d87-2a03-4407-bdf6-9265b725db09", "redirects": "禁止", "size_limit": 2097152, "user_agent": "HUANKAN/0.1 official-data-research", "content_type": "未发送", "timeout_seconds": 30}`

响应头：`{"date": "Sat, 10 Oct 2026 09:18:14 GMT", "server": "AkamaiGHost", "content-type": "text/html", "content-length": "418"}`

网络：`{"dns": ["23.50.28.98"], "proxy": ["DIRECT"], "tls_cipher": "TLS_AES_256_GCM_SHA384", "tls_protocol": "TLSv1.3", "environment_proxy_configured": false}`

响应开头：

```text
<HTML><HEAD>
<TITLE>Access Denied</TITLE>
</HEAD><BODY>
<H1>Access Denied</H1>
 
You don't have permission to access "http&#58;&#47;&#47;download&#46;bls&#46;gov&#47;pub&#47;time&#46;series&#47;cu&#47;cu&#46;series" on this server.<P>
Reference&#32;&#35;18&#46;d0bd2817&#46;1791623894&#46;b78aba1e
<P
```

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
