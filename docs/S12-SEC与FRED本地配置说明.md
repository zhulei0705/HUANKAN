# SEC与FRED本地配置

SEC没有API Key。SEC_CONTACT_EMAIL填写自己的真实有效联系方式，SEC_USER_AGENT填写应用或机构名称，SEC_FOLLOWED_CIKS仅填写从SEC官方公司映射搜索获得的十位CIK；SEC_ENABLED默认false。官方映射通过设置页主动刷新，未配置真实联系邮箱时不会请求。SEC官方访问政策见 https://www.sec.gov/about/developer-resources ，当前公开上限10次/秒，本地跨实例共享限流更严格，为2次/秒、120次/分钟。

FRED_API_KEY由用户在FRED账户申请，FRED_SERIES_WHITELIST填写用户明确需要的序列ID，逗号分隔，最多20项；FRED_ENABLED默认false。不要使用文档中的演示Key。官方说明：https://fred.stlouisfed.org/docs/api/fred/api_key.html 。每个序列的内容版权及可展示范围需分别核验，不能因接口可访问就假定获得再分发权。

本地凭证存放Windows用户级环境变量。PowerShell可用Read-Host输入并调用[Environment]::SetEnvironmentVariable(name,value,'User')保存；不要写入Git、报告或浏览器存储。设置后仅重新启动寰瞰后端，不需要重启PostgreSQL。

FRED观测日期不是官方发布时刻。代码保留真实value、units、observation_date、realtime_start和realtime_end；API只提供日期时，availability_time与published_at保持空，不能自行补成零点或采集时间。缺失值“.”单独标记，不转换为0。后续重复响应幂等，值或可得日期修订生成新证据版本。当前每序列最多1000条历史观测，超过预算明确失败，需单独实现或批准更大的分页任务；不截取后伪称全历史完整。

来源开关、配置门禁和数据库有效许可三者同时满足才可执行自动采集。空邮箱/空Key不阻塞FED、ECB和GDELT。

注意：realtime_start/realtime_end是API实时版本查询区间，不能自动认定为每条观测的首次发布或首次可得日期。未知的首次可得时刻保持NULL。
