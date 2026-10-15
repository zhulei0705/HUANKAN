# 只读恢复预检；本脚本没有启动、停止、端口切换或数据库写入指令。
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$jar=Join-Path $root 'backend/target/huankan-s70-reviewed-0.1.0.jar'
$expected='197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'
if((Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant() -ne $expected){throw '原版JAR不匹配'}
if($env:HUANKAN_DB_URL -ne 'jdbc:postgresql://127.0.0.1:5432/huankan' -or $env:HUANKAN_DB_USER -ne 'huankan_app'){throw '正式数据库连接身份变化'}
if(-not $env:HUANKAN_DB_PASSWORD -or -not $env:HUANKAN_ADMIN_TOKEN -or $env:HUANKAN_ADMIN_TOKEN.Length -lt 32){throw '凭证缺失或管理令牌不满足要求'}
if(-not(Test-Path 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe')){throw '指定Java21不存在'}
$listeners=@(Get-NetTCPConnection -State Listen -ErrorAction Stop)
$production=@($listeners|Where-Object LocalPort -eq 18080)
if($production.Count -gt 0){throw '18080已有监听，必须记录新身份并重新评审，不启动或停止'}
$postgres=@($listeners|Where-Object LocalPort -eq 5432)
if($postgres.Count -eq 0 -or @($postgres|Where-Object OwningProcess -ne 7072).Count -gt 0){throw '5432历史进程归属变化，必须重新核实'}
@{at=[datetimeoffset]::Now.ToString('o');account=[Security.Principal.WindowsIdentity]::GetCurrent().Name;jar=$jar;sha256=$expected;dbUrl=$env:HUANKAN_DB_URL;dbUser=$env:HUANKAN_DB_USER;port=18080;portFree=$true;postgresPid=7072;credentialValuesPrinted=$false;technicalAdmission='No-Go';blocking=@('外部更新与43884退出直接因果及独立托管未验收','正式旧版池监测和完整采集/SSE/页面恢复尚未验收','ECB悬挂任务处置需保全后独立审批');executedRecovery=$false}|ConvertTo-Json -Depth 5
