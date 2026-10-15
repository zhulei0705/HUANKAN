param([string]$ReplacementJar)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$currentPath=if(Test-Path "$root/runtime/s69-r1-current-test-process.json"){"$root/runtime/s69-r1-current-test-process.json"}else{"$root/runtime/s69-r1-test-process.json"}
$entry=Get-Content -Raw $currentPath|ConvertFrom-Json
$previousJavaId=$entry.javaPid
if($entry.schema -notmatch '^r1_web_[a-f0-9]{32}$' -or $env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '隔离JVM恢复目标不匹配'}
$live=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.javaPid)"
if(-not $live -or $live.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$entry.javaCreatedAt).ToUniversalTime().Ticks -or -not $live.CommandLine.Contains([IO.Path]::GetFileName($entry.jar)) -or -not $live.CommandLine.Contains('--server.port=18088')){throw 'JVM身份不匹配，拒绝关闭'}
if($ReplacementJar){if($ReplacementJar -notin @('huankan-s69-r1-reviewed-0.1.0.jar','huankan-s69-r1-reviewed-final-0.1.0.jar') -or -not(Test-Path "$root/backend/target/$ReplacementJar")){throw '只允许已完整构建的R1复核测试包'};$entry.jar="$root/backend/target/$ReplacementJar"}
$requested=(Get-Date).ToUniversalTime().ToString('o')
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18088/actuator/shutdown -Headers @{Authorization='Bearer isolated-s69-r1-test-token-32-characters'} -ContentType 'application/json' -Body '{}' -TimeoutSec 5 | Out-Null
$exited=$false
for($attempt=0;$attempt -lt 100;$attempt++){if(-not(Get-Process -Id $entry.javaPid -ErrorAction SilentlyContinue)){$exited=$true;break};Start-Sleep -Milliseconds 300}
if(-not $exited){throw '测试JVM未正常结束；保留现场，不强制终止任何Java'}
$stopped=(Get-Date).ToUniversalTime().ToString('o');$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$names=@('HUANKAN_DB_URL','HUANKAN_DB_USER','HUANKAN_DB_PASSWORD','HUANKAN_ADMIN_TOKEN','HUANKAN_COLLECTOR_ENABLED')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 $env:HUANKAN_DB_URL="$($env:HUANKAN_TEST_DB_URL)?currentSchema=$($entry.schema)";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN='isolated-s69-r1-test-token-32-characters';$env:HUANKAN_COLLECTOR_ENABLED='false'
 $p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-jar',$entry.jar,'--server.port=18088','--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=1','--spring.datasource.hikari.connection-timeout=500','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s69-r1-jvm-restored-$stamp.log" -RedirectStandardError "$root/runtime/s69-r1-jvm-restored-$stamp-error.log" -PassThru
 $jarDigest=[BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([IO.File]::ReadAllBytes($entry.jar))).Replace('-','')
 $restored=@{requestedAt=$requested;oldPid=$entry.javaPid;oldExitedAt=$stopped;newPid=$p.Id;newCreatedAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');schema=$entry.schema;jarSha256=$jarDigest;log="runtime/s69-r1-jvm-restored-$stamp.log"}
 $restored|ConvertTo-Json|Set-Content "$root/docs/evidence/s69-r1-jvm-lifecycle-$stamp.json" -Encoding utf8
 $restored|ConvertTo-Json|Set-Content "$root/runtime/s69-r1-restored-process.json" -Encoding utf8
 $entry.javaPid=$p.Id;$entry.javaCreatedAt=$restored.newCreatedAt;$entry.jarSha256=$jarDigest;$entry|ConvertTo-Json -Depth 6|Set-Content "$root/runtime/s69-r1-current-test-process.json" -Encoding utf8
}finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
Write-Output "仅测试JVM正常关闭并恢复：$previousJavaId→$($p.Id)，正式Java未操作。"
