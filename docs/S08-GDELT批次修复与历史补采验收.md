# S08 GDELT批次修复与历史补采

根因定位：旧代码从真实lastupdate清单提取文件名，没有本地15分钟向上取整生成未来批次的代码。旧日志显示06:00文件在05:53 UTC登记并返回404，06:10 UTC最终成功。旧程序未留存当时完整清单响应，因此只能根据代码路径、登记时间和随后恢复推断索引/文件可用时间差，不能把单条404当作根因定论。

最新索引现在原始字节+SHA-256留证，严格解析export、mentions、gkg三类同一批次、URL、字节数、MD5和UTC日期。future-time guard与retry_at双重阻止未来文件下载；本轮升级后提前下载观测数0。

历史完整索引受控流式读取，最多512MiB、三分钟；完整扫描每六小时至多一次（失败也计预算），不用每次轮询下载。按需脚本discover-gdelt-history-tail.mjs额外只读取8MiB官方索引尾部，严格检查HTTP206和Content-Range，未出现的条目不判定为不存在；本轮以该范围确认07:00缺口后补采。证据见s08-history-tail-check.json。独立发现扫描游标每次最多检查672个15分钟候选槽，跨调度继续推进；候选本身不授权下载。官方索引确认才登记任务，每轮最多三批、单来源数据库锁串行，ZIP每批最多10MiB，失败持久化/指数退避/最多8次自动尝试后停止自动重试。完成游标只在整个批次证据、版本、Outbox事务成功后推进；不是覆盖连续性断言，缺口单独记录。

已真实补齐：20261009051500（新增证据374）、20261009054500（新增证据315）、20261009061500（新增证据378）、20261009070000（新增证据362）。成功批次总数15。当前缺口：[{"batch_id":"20261009051500","state":"RESOLVED","first_checked_at":"2026-10-09T14:41:15.822132+08:00","last_checked_at":"2026-10-09T15:32:35.467656+08:00"},{"batch_id":"20261009054500","state":"RESOLVED","first_checked_at":"2026-10-09T14:41:15.823066+08:00","last_checked_at":"2026-10-09T15:32:35.467656+08:00"},{"batch_id":"20261009061500","state":"RESOLVED","first_checked_at":"2026-10-09T14:41:15.823585+08:00","last_checked_at":"2026-10-09T15:32:35.467656+08:00"},{"batch_id":"20261009070000","state":"RESOLVED","first_checked_at":"2026-10-09T15:05:46.529523+08:00","last_checked_at":"2026-10-09T15:32:35.467656+08:00"}]。历史回填及GDELT资料修订Outbox通知均为历史类型，无重大新消息提醒。

完整批次清单、实际HTTP和索引哈希见真实状态JSON。官方索引 https://data.gdeltproject.org/gdeltv2/lastupdate.txt ；历史清单由官方说明链接确认 https://blog.gdeltproject.org/gdelt-2-0-our-global-world-in-realtime/ 。隔离模拟停机槽、索引排队不推进游标、重复批次不重复证据与Outbox已通过。长于七天的遗漏按扫描游标和历史索引流量预算分轮推进，不声称已做数月停机的生产演练。

## 实际状态、测试与证据

核验时间：2026-10-09T07:34:34.668Z（UTC）。以下是查询快照，不是永远不变的实时数字。

| 来源 | 业务事件 | 证据版本 | Outbox | 官方来源证据确认 | 真实新增样本 |
|---|---:|---:|---:|---:|---:|
| ECB | 29 | 29 | 29 | 29 | 0 |
| FED | 20 | 20 | 20 | 20 | 0 |
| FRED | 0 | 0 | 0 | 0 | 0 |
| GDELT | 5037 | 5037 | 5037 | 0 | 0 |
| SEC | 0 | 0 | 0 | 0 | 0 |

正式库：127.0.0.1:5432/huankan；隔离测试库：独立55432/huankan_test。迁移：1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13，均成功。没有重建正式库或重启共用PostgreSQL。原始证据哈希错误0；正式业务Fixture 0。本轮初始2186条证据全部保留，缺失或改变0。上轮1,746条在该集合内，GDELT原1,697条随原证据完整保留。

实际累计采集任务统计（重复记录与重复批次单位分离）：

```json
[
  {
    "source_id": "ECB",
    "tasks": 44,
    "failed_tasks": 3,
    "parsed_or_selected": 1230,
    "new_evidence_versions": 29,
    "duplicate_records": 1201,
    "duplicate_batches": 0,
    "quarantined_jobs": 3
  },
  {
    "source_id": "FED",
    "tasks": 43,
    "failed_tasks": 3,
    "parsed_or_selected": 20,
    "new_evidence_versions": 0,
    "duplicate_records": 20,
    "duplicate_batches": 0,
    "quarantined_jobs": 3
  },
  {
    "source_id": "GDELT",
    "tasks": 30,
    "failed_tasks": 4,
    "parsed_or_selected": 5037,
    "new_evidence_versions": 5037,
    "duplicate_records": 0,
    "duplicate_batches": 2,
    "quarantined_jobs": 0
  }
]
```

异常隔离表6条，默认明确归类REQUEST；它们是请求/批次失败记录，不是同数量的伪造金融事实。GDELT按ZIP解析筛选排除的非目标记录存excluded_count，未伪造成事件。

实测命令：加载scripts/load-local-env.ps1；Java21；mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package -B：57项通过，0失败/错误/跳过；npm.cmd --prefix frontend test：5通过；npm.cmd --prefix frontend run build：通过；正式Playwright：4通过；隔离Playwright：1通过。原有测试均保留，新增官方索引、未来批次、UTC、历史队列/事务幂等、数据库互斥/预算/失效租约、FRED配置、告警去重和恢复测试。

日志：docs/evidence/s14-backend.log、s14-frontend-test.log、s14-frontend-build.log、s14-browser.log、s14-isolated-browser.log。真实状态：s-stage-actual-state.json、s14-reliability-state.json。

真实证据样例：
- FED：4e33ff75-dcdb-4fd1-b101-ecedb3ad9fd0；原始URL https://www.federalreserve.gov/newsevents/pressreleases/enforcement20261008a.htm；SHA-256 98ec882b93863aa2af3f928486c86db4cf2a0f2527252cf37fedcd9a81298672；下载字节14722，哈希吻合。
- ECB：8eb3be7a-8523-451b-9bce-fac7559d91c2；原始URL https://www.ecb.europa.eu//press/accounts/2026/html/ecb.mg261008~a10153d090.en.html；SHA-256 4bd9e1b1553a58d1f0fe7f3723ff57ac8e4b2c0395538d59ae525110d16c3a9d；下载字节5790，哈希吻合。
- GDELT：cd2b685a-ee20-423a-9d3b-14d0fa43e977；原始URL https://data.gdeltproject.org/gdeltv2/20261009051500.gkg.csv.zip；SHA-256 351c15e1d34b6352643777faea5c28b54c6de1723c7cbcfa62628c2ce77666fd；下载字节16573，哈希吻合。

当前阻塞：真实官方NEW_EVENT样本0，等待真实新增事件样本；SEC邮箱空，未正式发起接口请求；FRED Key与白名单未配置，未正式发起数据请求，许可待逐序列核验；没有获得或声称行情再分发授权。网络仍可能出现真实连接失败，失败日志保留，后续成功不会删除失败历史。

## 本轮实际修改文件

- backend/src/main/java/cn/huankan/collector/BodyDeadline.java
- backend/src/main/java/cn/huankan/collector/CollectorGuard.java
- backend/src/main/java/cn/huankan/collector/CollectorService.java
- backend/src/main/java/cn/huankan/collector/FredCollector.java
- backend/src/main/java/cn/huankan/collector/FredConfiguration.java
- backend/src/main/java/cn/huankan/collector/FredController.java
- backend/src/main/java/cn/huankan/collector/GdeltCollector.java
- backend/src/main/java/cn/huankan/collector/GdeltDownloader.java
- backend/src/main/java/cn/huankan/collector/GdeltIndex.java
- backend/src/main/java/cn/huankan/collector/GdeltInventory.java
- backend/src/main/java/cn/huankan/collector/OfficialHttpClient.java
- backend/src/main/java/cn/huankan/collector/SecAdapter.java
- backend/src/main/java/cn/huankan/collector/SecCompanyService.java
- backend/src/main/java/cn/huankan/event/ApiController.java
- backend/src/main/java/cn/huankan/event/MonitorScheduler.java
- backend/src/main/java/cn/huankan/event/QueryService.java
- backend/src/main/java/cn/huankan/evidence/EvidenceService.java
- backend/src/main/java/cn/huankan/stream/StreamService.java
- backend/src/main/resources/db/migration/V11__reliability_inventory.sql
- backend/src/main/resources/db/migration/V12__lease_fencing.sql
- backend/src/main/resources/db/migration/V13__inventory_scan_cursor.sql
- backend/src/test/java/cn/huankan/collector/BodyDeadlineTest.java
- backend/src/test/java/cn/huankan/collector/CollectorFailureTest.java
- backend/src/test/java/cn/huankan/collector/FredConfigurationTest.java
- backend/src/test/java/cn/huankan/collector/GdeltIndexTest.java
- backend/src/test/java/cn/huankan/collector/OfficialHttpClientTest.java
- backend/src/test/java/cn/huankan/evidence/DatabaseGateTest.java
- backend/src/test/java/cn/huankan/stream/LiveStreamTest.java
- frontend/e2e/multi-source.spec.ts
- frontend/src/api/client.ts
- frontend/src/App.vue
- frontend/src/composables/stream.ts
- frontend/src/tests/stream.test.ts
- scripts/backup-restore-drill.ps1
- scripts/capture-reliability.mjs
- scripts/capture-s-stage.mjs
- scripts/check-live-stream.mjs
- scripts/discover-gdelt-history-tail.mjs
- scripts/export-evidence-backup.mjs
- scripts/load-local-env.ps1
- scripts/observe-runtime.mjs
- scripts/start-services.ps1
- scripts/write-reliability-reports.mjs
