# Windows 本地部署指南

前置：Java21、Maven3.6.3+、Node20.19+或22.12+、PostgreSQL16+。本机已使用PostgreSQL18.6和Flyway11.20.3完成本阶段验收。Docker仅为可选启动方式。

1. 复制 .env.example 为 .env，生成不同的数据库密码与至少32字符管理密钥。不要提交 .env。
2. 已装 Docker 时在项目根执行 `docker compose --env-file .env up -d`；否则自行创建本地 PostgreSQL 空库 huankan 和同名用户，权限仅该库。不得清空已有库。
3. 在 PowerShell 进程设置 HUANKAN_DB_URL、HUANKAN_DB_USER、HUANKAN_DB_PASSWORD、HUANKAN_ADMIN_TOKEN。Spring不会自动读取根目录 .env，它是 Compose 和配置参考文件。
4. 运行 `powershell -ExecutionPolicy Bypass -File scripts/start-backend.ps1`。Flyway首次创建核心表，实际连接失败则启动失败，不回退假库。
5. 在 frontend 执行 `npm.cmd ci`、`npm.cmd run dev`，打开 http://127.0.0.1:5173 ，输入管理密钥。健康检查 http://127.0.0.1:18080/actuator/health 。8080保持其他项目使用。
6. FED本地Board RSS元数据用途已按官方免责声明核验；其他来源仍需单独核验。后台调度需 HUANKAN_COLLECTOR_ENABLED=true。SEC需 SEC_CONTACT_EMAIL（本人真实邮箱）、SEC_USER_AGENT（应用标识）、SEC_FOLLOWED_CIKS（官方映射选择的名单）；不需要SEC密码或API Key。
7. 执行 scripts/verify.ps1 重跑测试和构建。测试环境 Fixture不得进入正式业务库。

备份：使用PostgreSQL官方 pg_dump 对本地库备份，备份文件限制本机用户访问。恢复时创建新的隔离数据库，使用 pg_restore，验证记录/证据哈希及版本后再切换连接。未经批准不可覆盖正式库。当前备份恢复演练尚未执行。

默认回环监听，不提供公网部署验收；前端生产 dist 未由后端托管，首版用Vite开发代理。关闭服务用所在终端Ctrl+C，关闭Compose不要使用删除卷选项。

本机环境已将独立应用凭证放在当前Windows用户环境变量中；运行 `scripts/load-local-env.ps1` 加载到本进程。`scripts/start-services.ps1` 在后台启动本项目服务，`-RestartBackend`仅重启路径与创建时间匹配的本项目进程，`-StopBackend`仅停止该进程。日志在runtime；凭证不打印。不要把本机管理员口令写入.env或Git。用户要查看页面访问密钥，可在自己的PowerShell窗口读取HUANKAN_ADMIN_TOKEN并粘贴到密码输入框；页面仅保留内存。

完整复现：加载本地环境 → Maven package（打包前先停止本项目后端以免Windows锁JAR）→ start-services → verify。数据库测试使用HUANKAN_TEST_DB_URL/USER/PASSWORD，原生测试只接受huankan_test隔离库，缺环境时失败而非静默跳过。隔离Web测试先运行start-isolated-web，再在同一PowerShell进程的frontend目录执行 `npx.cmd playwright test -c playwright-isolated.config.ts`，结束后stop-isolated-web仅清理本次schema。不要给测试配置正式库。
