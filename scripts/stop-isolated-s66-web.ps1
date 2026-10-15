$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
$saved=Get-Content "$root\runtime\isolated-s66-web-process.json" -Raw|ConvertFrom-Json
if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test' -or $saved.schema -notmatch '^web_it_[a-f0-9]{32}$'){throw '测试隔离目标不匹配，拒绝清理'}
$process=Get-CimInstance Win32_Process -Filter "ProcessId=$($saved.processId)"
if($process){if(-not $process.CommandLine.Contains("$root\backend\target\huankan-s66-accepted-0.1.0.jar") -or -not $process.CommandLine.Contains('--server.port=18084') -or $process.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$saved.createdAt).ToUniversalTime().Ticks){throw '进程不匹配，拒绝停止'};Stop-Process -Id $saved.processId}
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{& 'D:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -p 55432 -U huankan_test -d huankan_test -w -v ON_ERROR_STOP=1 -c "DROP SCHEMA $($saved.schema) CASCADE";if($LASTEXITCODE -ne 0){throw '隔离测试schema清理失败'}}finally{Remove-Item Env:PGPASSWORD}
Write-Output '仅清理本次隔离测试schema；正式库未操作。'




