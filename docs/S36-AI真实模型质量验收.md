# S36 AI真实模型质量验收

本次实际AI状态：AI 模型尚未配置；规则分析仍可工作。配置状态false，正式执行0，正式失败0。未提供模型、接口和真实凭证，没有付费调用，没有模型质量或模型收益验收。

增量保留原AI证据范围和不可覆盖结果，新增provider、model_version、实际input/output tokens、来源引用、日请求预算及输出上限。外部模型预算默认0，未配置预算禁止请求；请求60秒超时，输出上限默认1024、最大4096。本实现不自动重试付费模型，避免重复费用。未取得可靠费用依据时cost=null，不能填写0费用。

独立程序检查原文引用、数值、日期、币种、单位、比较口径、公司/指标标识和工具输出。新增百分点与百分比混用拒绝。隔离模型兼容协议、99%幻觉、503失败及恶意工具输出等测试只能说明接口/校验程序行为，不是实际模型质量评价。真实模型公司/指标语义、幻觉与提示注入表现仍待实际运行检验。
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
