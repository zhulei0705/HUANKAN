# T02-D SSE 实时推送验收

说明：本报告正文保留T阶段当时的20条基线。共享文件evidence/persistence-restart.json和evidence/real-sse-replay.json已更新为最新S阶段实际验证；当前数量与状态见S07-综合回归验收报告.md及统一开发状态报告.md。


主责R11/R12/R20/R21/R25/R26；2026-10-09，北京时间。保留现有Outbox和SseEmitter实现，增量修补，未重写实时系统。

## 实际变更

`EvidenceService` 事务保存证据/事件/版本/Outbox/监控起点；全事务advisory锁保护序号与提交顺序。V4增加通知类型，回填与新增分开。`StreamService` 保持每轮100条、50连接、持久化Last-Event-ID补读，新增快照游标和服务关闭时完成连接。`SecurityConfig` 修复SSE完成/断开时异步派发鉴权错误，初始请求仍需合法密钥。

前端保持fetch SSE，游标在刷新成功后推进，忽略已处理消息；300毫秒合并或最多100条批量刷新，列表最多50条。新增显式浏览器离线处理：离线时取消既有流、显示“实时连接已断开”、重连沿用已确认游标。历史回填不增加新提醒计数。生产/测试Vite代理分离，测试库不能连正式代理。

## A：真实历史数据

生产库实际20事件、20证据、20持久化Outbox，全部历史回填。`scripts/check-live-stream.mjs` 对真实后端发出两个独立HTTP连接，各接收20条；编号一致、唯一20；断开后Last-Event-ID10重连补读10条，未重复已处理消息。真实新增类型数量0。样例、原文HTTP200、哈希、入库/重复/隔离、重启证据清单见T03/T04及JSON。

正式中文Web双浏览器均连接成功，显示真实官方消息、历史状态和证据；桌面/手机通过，50条分页上限。`npx.cmd playwright test` 共3项通过。记录 `real-sse-replay.json`、`stage2-real-browser.log`。

## B：隔离测试环境

PostgreSQL55432/huankan_test随机schema，后端18081，前端5175，后台采集关闭。仅这里创建标有“隔离测试”的Fixture，不计入正式20条。测试完成后按schema严格检查并清理，不触碰业务库。

JUnit `LiveStreamTest` 实际Spring Boot随机HTTP端口与原生PostgreSQL：双客户端自动接收事务提交的消息；断开期间仍能持久化，重连按ID补读；新增/历史类型正确。浏览器隔离用例进一步验证真实事务→Outbox→SSE→Vue无需刷新自动更新，浏览器离线状态及重连、同一事件不重复显示、历史回填不提醒。

隔离用例导入250条历史Fixture，共253条测试事件；页面仅显示50条，服务每轮最多100条补读，客户端合并刷新，浏览器继续响应。该测试为有界机制验收，不是百万消息或长期性能压测。

命令：`start-isolated-web.ps1` → `npx.cmd playwright test -c playwright-isolated.config.ts` → `stop-isolated-web.ps1`。1项浏览器用例通过，清理成功。所有后端30项通过/0跳过；Vitest协议5项通过。记录 `stage2-isolated-browser.log`、`isolated-web-cleanup.log`、`stage2-final-tests.log`。

## 限制

A未观测官方新发布事件，因此没有真实发布→页面延迟结果；B通过不替代A的新事件验证。发送成功不等于用户已读。消息通知游标仅当前页面会话内保留，页面刷新使用快照游标；尚未提供用户级持久化已读、Outbox保留/压缩和长周期负载测量。30分钟以上断网、50客户端满载、服务灾备恢复未测。

