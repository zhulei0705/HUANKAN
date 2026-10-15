# S33 GLEIF法人身份核验验收

实际官方API HTTP200保存Apple Inc.（HWUPKR0MPOU8FGXBT394）和APPLE CANADA INC.（5493004SYPRAVRVNK561）。法人实体2、登记版本2、并表/例外4、原始证据6。首次Apple保存3份证据，即时重复0份；加拿大保存3份。最近再次重复同样没有新增证据，以真实请求/任务记录为准。

名称搜索Apple返回多个不同法人，搜索Apple Inc.也包含其他名称。只按20字符LEI、mod97校验位、官方id/attributes.lei及名称/司法辖区等字段核验；不模糊合并，不生成来源未给出的中文法律名称或证券代码。司法辖区、地址、登记状态、源内更新时间、采集时间与完整JSON保留。

GLEIF许可依据：[LEI数据条款](https://www.gleif.org/en/meta/lei-data-terms-of-use)、[官方API](https://www.gleif.org/en/lei-data/gleif-api/)、[数据访问说明](https://www.gleif.org/en/lei-data/access-and-use-lei-data)。2026-10-10实查条款CC0。API基于Golden Copy；官方说明有每日LEI报告，不能推断API严格实时。当前数值速率上限未确认，本地持久化限流最多2次/秒、120次/分钟，429遵从冷却，403及5xx保留失败。

首次实际HTTP200后保存失败，原因是旧证据约束要求真实新闻发布时间。未绕过该约束：原始证据保持PENDING，新闻发布时间null；另以法人字段校验记录说明源内身份核对。登记更新时间不改写为新闻发布时间。失败任务与HTTP200响应均留存。

[Level 2范围](https://www.gleif.org/en/lei-data/access-and-use-lei-data/level-2-data-relationship-record-rr-cdf-2-1-format)：加拿大→Apple的直接/最终会计并表；Apple的报告例外为NATURAL_PERSONS。并表证明会计口径，不证明股权比例、供应链或证券关系。加拿大关系披露corroborationLevel=ENTITY_SUPPLIED_ONLY，公开时刻未知，保持待审核。

真实登记样例：

```json
{
  "evidence_id": "e2838a0d-5376-4c77-84c2-9a2b12d57bbd",
  "entity_id": "49949173-331e-4185-9192-594aa460ecbc",
  "lei": "HWUPKR0MPOU8FGXBT394",
  "legal_name": "Apple Inc.",
  "jurisdiction": "US-CA",
  "legal_address": {
    "city": "Glendale",
    "region": "US-CA",
    "country": "US",
    "language": "en",
    "postalCode": "91203",
    "mailRouting": null,
    "addressLines": [
      "C/O C T Corporation System",
      "330 N. Brand Blvd",
      "Suite 700"
    ],
    "addressNumber": null,
    "addressNumberWithinBuilding": null
  },
  "entity_status": "ACTIVE",
  "registration_status": "ISSUED",
  "last_update": "2026-03-04T00:34:33+08:00",
  "attributes": {
    "bic": [
      "APLEUS66XXX"
    ],
    "gem": null,
    "lei": "HWUPKR0MPOU8FGXBT394",
    "mic": null,
    "qcc": null,
    "ocid": null,
    "entity": {
      "status": "ACTIVE",
      "category": "GENERAL",
      "legalForm": {
        "id": "H1UM",
        "other": null
      },
      "legalName": {
        "name": "Apple Inc.",
        "language": "en"
      },
      "expiration": {
        "date": null,
        "reason": null
      },
      "otherNames": [
        {
          "name": "Apple Computer, Inc.",
          "type": "PREVIOUS_LEGAL_NAME",
          "language": "en"
        }
      ],
      "eventGroups": [],
      "subCategory": null,
      "creationDate": "1977-01-03T00:00:00Z",
      "jurisdiction": "US-CA",
      "legalAddress": {
        "city": "Glendale",
        "region": "US-CA",
        "country": "US",
        "language": "en",
        "postalCode": "91203",
        "mailRouting": null,
        "addressLines": [
          "C/O C T Corporation System",
          "330 N. Brand Blvd",
          "Suite 700"
        ],
        "addressNumber": null,
        "addressNumberWithinBuilding": null
      },
      "registeredAs": "806592",
      "registeredAt": {
        "id": "RA000598",
        "other": null
      },
      "otherAddresses": [],
      "successorEntity": {
        "lei": null,
        "name": null
      },
      "associatedEntity": {
        "lei": null,
        "name": null
      },
      "successorEntities": [],
      "headquartersAddress": {
        "city": "Cupertino",
        "region": "US-CA",
        "country": "US",
        "language": "en",
        "postalCode": "95014",
        "mailRouting": null,
        "addressLines": [
          "One Apple Park Way"
        ],
        "addressNumber": null,
        "addressNumberWithinBuilding": null
      },
      "transliteratedOtherNames": []
    },
    "spglobal": [
      "24937"
    ],
    "registration": {
      "status": "ISSUED",
      "managingLou": "5493001KJTIIGC8Y1R12",
      "validatedAs": "806592",
      "validatedAt": {
        "id": "RA000598",
        "other": null
      },
      "lastUpdateDate": "2026-03-03T16:34:33Z",
      "nextRenewalDate": "2027-03-08T17:27:20Z",
      "corroborationLevel": "FULLY_CORROBORATED",
      "initialRegistrationDate": "2012-06-06T15:53:00Z",
      "otherValidationAuthorities": []
    },
    "conformityFlag": "CONFORMING"
  },
  "source_url": "https://api.gleif.org/api/v1/lei-records/HWUPKR0MPOU8FGXBT394",
  "raw_content_hash": "f1a7124f7264cb8253d64809fe8fecbd95fded68b8a9d8b50e14c24f043789c4",
  "collected_at": "2026-10-10T11:50:07.13404+08:00"
}
```
## 本次数据库与测试证据

查询快照：2026-10-10T12:52:34.453743+08:00。后台采集持续运行，计数只对应此快照。

| 指标 | 实际数量 |
|---|---:|
| 原始证据 | 38663 |
| 业务事件 | 38657 |
| 采集Outbox | 38657 |
| 正式资格审核通知位置 | 0 |
| 原子事实记录 | 1315 |
| 待核对或冲突事实 | 0 |
| 快照内条件性假设条目（非独立结论） | 12 |
| 累计客户端确认投递 | 145 |
| 结构化字段一致性 | 263 |
| 官方发布或登记字段确认 | 1052 |
| 独立来源支持 | 0 |
| GLEIF法人版本 | 2 |
| GLEIF法人实体 | 2 |
| GLEIF并表/例外版本 | 4 |
| 已审核关系 | 0 |
| 待审核关系 | 2 |
| 规则研判快照 | 6 |
| 实际AI执行 | 0 |
| 实际AI失败 | 0 |
| 累计隔离记录 | 50 |
| 原始宏观观测 | 263 |
| ECB本地历史版本 | 119 |
| 真实历史修订 | 0 |
| 重复Outbox | 0 |
| 正式库Fixture | 0 |
| 来源与任务绑定错误 | 0 |
| 已扫描哈希失败 | 0 |
| 非GLEIF证据缺少业务关联 | 0 |

原始证据来源数量：

| 来源 | 数量 |
|---|---:|
| ECB | 29 |
| ECB_DATA | 263 |
| FED | 21 |
| GDELT | 37344 |
| GLEIF | 6 |
| NBS | 1000 |

任务累计统计不能替代本次新增入库数：

| 来源 | 状态 | 任务 | 实际解析/获取计数 | 入库计数 | 重复计数 | 隔离计数 |
|---|---|---:|---:|---:|---:|---:|
| ECB | FAILED | 35 | 0 | 0 | 0 | 35 |
| ECB | SUCCESS | 252 | 7560 | 29 | 7531 | 0 |
| ECB_DATA | FAILED | 6 | 65 | 0 | 65 | 4 |
| ECB_DATA | SUCCESS | 31 | 5898 | 382 | 5516 | 0 |
| FED | FAILED | 8 | 0 | 0 | 0 | 8 |
| FED | SUCCESS | 291 | 100 | 21 | 79 | 0 |
| GDELT | FAILED | 81 | 1458 | 1458 | 0 | 0 |
| GDELT | SUCCESS | 101 | 35886 | 35886 | 0 | 0 |
| GLEIF | FAILED | 2 | 0 | 0 | 0 | 0 |
| GLEIF | SUCCESS | 6 | 18 | 6 | 12 | 0 |
| NBS | FAILED | 3 | 0 | 0 | 0 | 3 |
| NBS | SUCCESS | 39 | 39000 | 1000 | 38000 | 0 |

后端 110 项通过，失败0、错误0、跳过0；原104项均保留。前端5项通过及构建成功。正式浏览器9项通过（原8项保留，新增事实/法人/研判验收）；隔离浏览器1项通过。

执行命令：先加载 scripts/load-local-env.ps1，再使用 Java21：

`mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package`

在frontend：`npm run test -- --run`、`npm run build`、`npx playwright test --reporter=line`。隔离schema由start-isolated-web.ps1创建，`npx playwright test -c playwright-isolated.config.ts`完成后由stop-isolated-web.ps1仅清理本次schema。

最终日志：runtime/s38-durable-qualification-package.log、runtime/s38-frontend-tests-delivery.log、runtime/s38-frontend-build-delivery.log、runtime/s38-browser-formal-delivery.log、runtime/s38-browser-isolated.log。首轮测试与浏览器失败日志保留，不把失败阶段描述为通过。

证据：docs/evidence/s38-delivery-state.json、s38-state.json（全字节扫描时点）、s38-ledger-integrity.json、s38-evidence-samples.json、s38-real-api-exercises.json。

## 修改文件与范围

详见 docs/evidence/s38-modified-files.json 的完整文件清单。新增迁移V30—V39；V1—V29的历史checksum逐项未变。未重建正式库、未删除历史证据、未重启共享PostgreSQL服务。只重启经过JAR路径和创建时间核对的寰瞰应用。

## 当前剩余验收事项

真实官方新增提交时双浏览器在线接收并渲染：当期样本0，待验证。24小时连续稳定性：尚未完成，不能把多段观测拼为24小时。实际AI模型：未配置，正式调用0，模型质量待验收。SEC真实联系邮箱/关注配置和FRED凭证/内容许可仍不足，保持禁用。ECB历史119个本地版本、真实修订0，source vintage未知。行业归属、证券发行、真实客户供应商、金融披露和资本开支实质事件仍缺正式合格样本，不能凭GLEIF并表或名称共现补造。

角色责任按用户要求逐项检查，由本次开发与测试证据支撑；未声称26位人员独立签字或外部专家认证。
