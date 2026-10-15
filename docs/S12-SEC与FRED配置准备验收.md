# S12 SEC与FRED配置准备

SEC：SEC 联系邮箱尚未配置，自动采集未启用。；映射0条，联系配置false，许可false。联系方式预检、官方CIK搜索/关注、原始映射留证、逐CIK提交后增量高水位、表单类型及/A修订分类、证据版本和共享限流代码已准备。仍会重读近期申报并以accession和内容版本去重，避免高水位过滤漏掉已有资料修订。申报修订与原始申报之间的法定关联尚未做真实样本验收。

FRED：FRED API Key尚未配置，自动采集未启用。；实际请求0，HTTP NULL（未发起正式请求），观测0，证据0。Key、白名单、来源许可门禁；真实元数据/观测响应、值/单位/日期/缺失值、实时版本区间及修订留证代码已加入。1000条历史预算超限明确失败，完整大序列分页仍待扩展；版本区间日期不能冒充首次可得日期或秒级发布时间，availability_time保持未知。SEC/FRED配置测试通过，不等于正式连通验收。

各来源实际请求次数：[{"id":"ECB","actual_requests":87,"last_actual_request":"2026-10-09T15:34:18.494+08:00"},{"id":"FED","actual_requests":44,"last_actual_request":"2026-10-09T15:34:17.211+08:00"},{"id":"FRED","actual_requests":0,"last_actual_request":null},{"id":"GDELT","actual_requests":39,"last_actual_request":"2026-10-09T15:32:35.584922+08:00"},{"id":"SEC","actual_requests":0,"last_actual_request":null}]。本地配置详见S12-SEC与FRED本地配置说明.md。官方依据 https://www.sec.gov/about/developer-resources 、https://fred.stlouisfed.org/docs/api/fred/ 、https://fred.stlouisfed.org/docs/api/fred/series_observations.html 。

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
