# T02-F SEC 配置准备报告

2026-10-09，北京时间。主责R08/R09/R10/R12/R21/R23/R26。结论：配置与官方公司映射搜索关注接口已实现；真实联系方式为空，SEC保持待配置/关闭，未宣称成功接入。

## 配置与实际修改

新增 `SecConfiguration.java`、`SecCompanyService.java`、`SecController.java`；修改application.yml、CollectorService、OfficialHttpClient、.env.example、系统设置页面。V4新增sec_company_registry与sec_followed_company，两表当前实际0条。

环境变量：SEC_CONTACT_EMAIL必须由用户填写真实有效联系方式；SEC_USER_AGENT可设置机构/应用标识，客户端确保附上联系方式；SEC_FOLLOWED_CIKS为逗号分隔CIK。默认全空，不填虚构邮箱，不需要SEC登录密码或API Key。只有联系方式、名单和来源许可/启用状态满足后才可能执行采集。本次未调用SEC接口。

用户配置示意（不包含假的邮箱值）：在本机PowerShell使用 `Read-Host` 读取本人邮箱后设置 `$env:SEC_CONTACT_EMAIL`；设置 `$env:SEC_USER_AGENT='HUANKAN 本地研究阅读器'` 时建议使用HTTP可识别ASCII标识 `HUANKAN/0.1`；按官方映射搜索结果选择CIK。重启后端加载进程环境，或由用户保存对应用户环境变量。不要把管理员登录信息给系统。

## 官方依据与接口

[SEC官方API说明](https://www.sec.gov/search-filings/edgar-application-programming-interfaces)：公开数据API无API Key要求。[EDGAR访问文档](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data)：声明User-Agent，当前最多10请求/秒；官方公司证券代码/CIK映射入口 `https://www.sec.gov/files/company_tickers.json`。该映射官方不保证范围或准确性，不把证券标识映射当公司业务关系。

实现的配置接口（全部鉴权）：POST `/api/sec/status`、`/companies/refresh`、`/companies/search`、`/companies/follow`。未提供联系方式时refresh被阻断；搜索只查真实官方映射库，结果最多30条；添加关注受外键约束，只能添加映射中存在的10位CIK，无来源不明的硬编码公司。Collector优先环境名单，空时使用已保存关注名单。

SEC数据和映射请求都进入统一客户端：专用SEC时间门禁默认每秒2次，同时服从全局串行限制；禁止跳转和非白名单URL。403直接失败不重试；429按Retry-After暂停该域名；5xx最多3次，失败记FAILED且不生成业务证据。

## 实测状态与测试

正式 `/api/sec/status` 返回“待配置真实联系邮箱”，contact_configured=false，mapping_count=0，followed=[]，记录 `evidence/sec-configuration-status.json`。SEC正式HTTP连通未测，公司映射0、关注0、SEC采集/入库/重复/隔离均未执行，不用0记录冒充已测试。

后端30项通过/0跳过，其中SEC空联系方式/占位邮箱阻断3项、HTTP403/429冷却/503三次上限3项（隔离输运Fixture，未发SEC真实请求）、采集HTTP失败/网络中断4项。正式数据库与美联储统计、证据样例、SSE及浏览器结果见T02-T05报告；正式Web系统设置保持中文待配置状态。

剩余：用户真实邮箱与关注公司、官方映射实际下载验证、SEC许可核验、正式HTTP连通、SEC历史文件/companyfacts、多实例/跨机器限流。本次限流仅单进程，所有机器的10次官方总限制需用户在多部署场景另行统一控制。未替用户伪造身份、访问SEC或申请付费服务。
