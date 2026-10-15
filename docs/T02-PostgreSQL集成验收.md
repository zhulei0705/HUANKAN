# T02-A PostgreSQL 集成验收

说明：本报告正文保留T阶段当时的20条基线。共享文件evidence/persistence-restart.json和evidence/real-sse-replay.json已更新为最新S阶段实际验证；当前数量与状态见S07-综合回归验收报告.md及统一开发状态报告.md。


验收日期：2026-10-09，北京时间。主责 R12/R14/R25/R26。结论：通过本机真实数据库、迁移、隔离测试及应用重启持久化验证。

## 实际环境及变更

最初发现已运行的 `postgresql-x64-18`，安装目录 `D:\Program Files\PostgreSQL\18`，Docker未在PATH找到。未提供凭证时连接返回 `fe_sendauth: no password supplied`，使用同一套已安装程序在项目runtime建立独立测试实例（55432），未修改原服务。用户随后提供凭证，正式业务优先复用既有5432服务，创建新的 `huankan` 库、非超级用户 `huankan_app`；未改动其他数据库、角色、容器或卷。管理员密码仅用于本次进程连接，不写入项目。

业务：PostgreSQL18.6 / 127.0.0.1:5432 / huankan。测试：独立55432 / huankan_test / huankan_test角色，每次测试创建随机schema并只清理本次schema。此前独立实例中的空huankan库保留且不用作业务库，不混入正式统计。

修改：`scripts/start-local-postgres.ps1`、`configure-existing-postgres.ps1`、`load-local-env.ps1`、`start-services.ps1`、`check-persistence.ps1`；`application.yml`；`pom.xml`；新增V4迁移；`DatabaseGateTest.java`。密码和访问密钥通过本地用户环境变量载入进程，报告与命令输出不含实际值。

Flyway原11.7.2提示PostgreSQL18未验证；升级至11.20.3，按官方版本源码支持18，并经过实际迁移及回归。依据：https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database 、https://github.com/flyway/flyway/blob/flyway-11.20.3/flyway-database/flyway-database-postgresql/src/main/java/org/flywaydb/database/postgresql/PostgreSQLDatabase.java 。

## 实际数据库结果

V1-V4全部成功；重启再次校验4个迁移，显示无需迁移。public共15表（含Flyway历史表）、23索引、17外键、3唯一约束、15主键、11检查约束。应用账号不是超级用户。证据/事件/Outbox各20，隔离0，哈希不匹配0，Fixture标识0。详见 `evidence/stage2-database-final.json`。

健康：`http://127.0.0.1:18080/actuator/health` 返回UP。8080为其他Java项目，本次保留，寰瞰改为回环18080。

## 测试与持久化

命令：加载 `scripts/load-local-env.ps1`，Java21下运行 `mvn.cmd -s scripts/maven-settings.xml -f backend/pom.xml package -B`。最终30项后端测试通过，失败0、错误0、跳过0。数据库7项实际运行，涵盖迁移重复执行、幂等、全事务回滚、版本及回退历史、不可修改、过期许可、哈希错误、未来时间拒绝。不是H2或Mock数据库结果。

`scripts/check-persistence.ps1` 重启本项目后端，再次读取全部证据ID和字节哈希清单。前后20份证据清单哈希一致：`e1ffd9a0af2bbbcdf055aecbdbad9f0533b48ae8d55e312c56d6a1bb39164ffd`；健康UP。没有重启其他项目使用的PostgreSQL服务，没有清空数据。记录：`evidence/persistence-restart.json`。

## 共同业务验收与限制

真实RSS首次解析20、入库20、重复0；强制HTTP200重抓解析20、新增0、重复20；业务隔离0。真实证据样例、官方HTTP、SSE和浏览器结果见T04/T05报告及相关JSON。浏览器正式历史数据3项、隔离推送1项通过。

备份恢复演练、Windows开机自启动、长时间负载稳定性尚未执行。既有5432监听配置保持原样；寰瞰服务只连回环。本次不是完整V4.0最终发布验收。

