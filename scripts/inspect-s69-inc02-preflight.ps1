# 只读正式恢复预检；仅在workspace创建/删除自己的临时可写性文件。
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
. "$PSScriptRoot/load-local-env.ps1"
$expectedJar='197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'
$jar="$root/backend/target/huankan-s70-reviewed-0.1.0.jar"
$checks=@();function Check([string]$name,[bool]$passed,$details){$script:checks+=@{name=$name;passed=$passed;details=$details}}
Check '原版文件及冻结SHA' ((Get-FileHash $jar).Hash.ToLowerInvariant() -eq $expectedJar) $jar
$java='C:/Program Files/Java/jdk-21.0.10/bin/java.exe';$version=& $java -version 2>&1|Out-String
Check 'Java21实际可执行' ($LASTEXITCODE -eq 0 -and $version -match '21\.0\.10') $version
Check '正式连接配置' ($env:HUANKAN_DB_URL -eq 'jdbc:postgresql://127.0.0.1:5432/huankan' -and $env:HUANKAN_DB_USER -eq 'huankan_app') @{url=$env:HUANKAN_DB_URL;user=$env:HUANKAN_DB_USER}
Check '凭证存在且管理令牌足长' ([bool]$env:HUANKAN_DB_PASSWORD -and $env:HUANKAN_ADMIN_TOKEN.Length -ge 32) '不记录凭证值'
$listeners=@(Get-NetTCPConnection -State Listen)
Check '18080空闲' (@($listeners|Where-Object LocalPort -eq 18080).Count -eq 0) @($listeners|Where-Object LocalPort -eq 18080|Select-Object LocalAddress,OwningProcess)
$account=[Security.Principal.WindowsIdentity]::GetCurrent().Name
Check '计划账户匹配' ($account -eq 'DESKTOP-JMS8B0H\sthl') $account
$temporary=Join-Path "$root/runtime" ('inc02-write-check-'+[guid]::NewGuid().ToString('N')+'.tmp')
try{[IO.File]::WriteAllText($temporary,'INC02日志目录可写性检查');Check '日志目录可写' (Test-Path $temporary) "$root/runtime"}finally{if(Test-Path -LiteralPath $temporary){Remove-Item -LiteralPath $temporary}}
$gate=Get-Content "$dest/recovery-gate.json" -Raw|ConvertFrom-Json
Check '独立托管实际验收' ([bool]$gate.hostValidated) '系统任务注册/账户验证/维护独立性尚未执行'
Check '正式任务状态写入获批准' ([bool]$gate.formalWriteApproval) 'ECB补偿事务与原版自动OWNER_LOST写入均需批准'
Check '正式Java启动获批准' ([bool]$gate.formalStartApproved) '本轮明示禁止正式启动'
@{at=[datetimeoffset]::Now.ToString('o');checks=$checks;technicalAdmission='No-Go';allChecksPassed=(@($checks|Where-Object passed -eq $false).Count -eq 0);startedJava=$false;formalDbWritten=$false}|ConvertTo-Json -Depth 8|Set-Content "$dest/preflight.json" -Encoding utf8
Write-Output '自动预检已保存；独立托管与正式操作授权未满足，No-Go。'
