$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$current=Get-Content -Raw "$root/runtime/s69-r1-current-test-process.json"|ConvertFrom-Json
$initial=Get-Content -Raw "$root/runtime/s69-r1-test-process.json"|ConvertFrom-Json
if($current.schema -notmatch '^r1_web_[a-f0-9]{32}$' -or $env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '测试目标不匹配'}
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss';$started=(Get-Date).ToUniversalTime().ToString('o')
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{
 & 'D:/Program Files/PostgreSQL/18/bin/pg_dump.exe' -h 127.0.0.1 -p 55432 -U $env:HUANKAN_TEST_DB_USER -d huankan_test -w --schema $current.schema --no-owner --no-privileges -f "$root/docs/evidence/s69-r1-isolated-schema-$stamp.sql"
 if($LASTEXITCODE -ne 0){throw '本次测试schema导出失败，保留实例'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$java=Get-CimInstance Win32_Process -Filter "ProcessId=$($current.javaPid)"
if($java){
 if($java.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$current.javaCreatedAt).ToUniversalTime().Ticks -or -not $java.CommandLine.Contains([IO.Path]::GetFileName($current.jar)) -or -not $java.CommandLine.Contains('--server.port=18088')){throw '仅测试Java身份不匹配，拒绝关闭'}
 Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18088/actuator/shutdown -Headers @{Authorization='Bearer isolated-s69-r1-test-token-32-characters'} -ContentType 'application/json' -Body '{}' -TimeoutSec 5 | Out-Null
 for($i=0;$i -lt 100;$i++){if(-not(Get-Process -Id $current.javaPid -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 300}
 if(Get-Process -Id $current.javaPid -ErrorAction SilentlyContinue){throw '测试Java未正常停止，未强制停止任何Java'}
}
$static=Get-CimInstance Win32_Process -Filter "ProcessId=$($initial.staticPid)"
if($static){if($static.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$initial.staticCreatedAt).ToUniversalTime().Ticks -or -not $static.CommandLine.Contains('serve-s69-static.mjs') -or -not $static.CommandLine.Contains('5184')){throw '仅测试静态进程身份不匹配'};Stop-Process -Id $initial.staticPid}
@{startedAt=$started;finishedAt=(Get-Date).ToUniversalTime().ToString('o');javaPid=$current.javaPid;staticPid=$initial.staticPid;schema=$current.schema;schemaRetained=$true;dump="docs/evidence/s69-r1-isolated-schema-$stamp.sql";formalJavaTouched=$false;postgresServiceTouched=$false}|ConvertTo-Json|Set-Content "$root/docs/evidence/s69-r1-test-cleanup-$stamp.json" -Encoding utf8
Write-Output '仅本轮测试Java18088与静态5184已正常结束；测试schema及导出证据保留。正式Java、5173、开发5181、候选5182及两个PostgreSQL服务未操作。'
