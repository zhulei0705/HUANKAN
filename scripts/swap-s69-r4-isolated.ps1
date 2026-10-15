param([ValidateSet('candidate','rollback')][string]$Version='rollback')
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$infoPath="$root/runtime/s69-r4-owned-process.json"
$entry=Get-Content $infoPath -Raw|ConvertFrom-Json
if($entry.schema -notmatch '^r1_web_[a-f0-9]{32}$' -or $entry.database.port -ne 55434 -or $entry.database.database -ne 'huankan_r4_test' -or $entry.database.directory.Replace('\','/') -ne "$root/runtime/s69-r4-owned-db/data".Replace('\','/')){throw '拒绝非本轮独占隔离目标'}
$live=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.javaPid)"
if(-not $live -or $live.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$entry.javaCreatedAt).ToUniversalTime().Ticks -or -not $live.CommandLine.Contains($entry.jar) -or -not $live.CommandLine.Contains('--server.port=18088')){throw '隔离进程身份不匹配'}
$jar=if($Version -eq 'candidate'){"$root/backend/target/huankan-s69-r3-candidate-0.1.0.jar"}else{"$root/backend/target/huankan-s70-reviewed-0.1.0.jar"}
$expected=if($Version -eq 'candidate'){'a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61'}else{'197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'}
if((Get-FileHash $jar).Hash.ToLower() -ne $expected){throw 'JAR哈希不匹配'}
$started=(Get-Date).ToUniversalTime().ToString('o');$oldPid=$entry.javaPid
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18088/actuator/shutdown -Headers @{Authorization='Bearer isolated-s69-r1-test-token-32-characters'} -ContentType 'application/json' -Body '{}' -TimeoutSec 5|Out-Null
for($n=0;$n -lt 100;$n++){if(-not(Get-Process -Id $oldPid -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 300}
if(Get-Process -Id $oldPid -ErrorAction SilentlyContinue){throw '隔离Java未优雅停止，未强制终止'}
$stopped=(Get-Date).ToUniversalTime().ToString('o');$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55434/huankan_r4_test?currentSchema=$($entry.schema)";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN='isolated-s69-r1-test-token-32-characters';$env:HUANKAN_COLLECTOR_ENABLED='false'
$process=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-jar',$jar,'--server.port=18088','--spring.datasource.hikari.maximum-pool-size=10','--spring.datasource.hikari.minimum-idle=10','--spring.datasource.hikari.connection-timeout=30000','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,metrics,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s69-r4-$Version-$stamp.log" -RedirectStandardError "$root/runtime/s69-r4-$Version-$stamp-error.log" -PassThru
$entry.javaPid=$process.Id;$entry.javaCreatedAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)").CreationDate.ToUniversalTime().ToString('o');$entry.jar=$jar;$entry.jarSha256=$expected
$entry|ConvertTo-Json -Depth 8|Set-Content $infoPath -Encoding utf8
$healthy=$false
for($n=0;$n -lt 100;$n++){try{$h=Invoke-RestMethod http://127.0.0.1:18088/actuator/health -TimeoutSec 2;if($h.status -eq 'UP'){$healthy=$true;break}}catch{};Start-Sleep -Milliseconds 500}
@{started=$started;oldPid=$oldPid;oldExitedAt=$stopped;healthyAt=(Get-Date).ToUniversalTime().ToString('o');version=$Version;healthy=$healthy;newPid=$process.Id;schema=$entry.schema;jar=$jar;sha256=$expected;formalTouched=$false}|ConvertTo-Json|Set-Content "$root/docs/evidence/s69-r4-swap-$Version-$stamp.json" -Encoding utf8
if(-not $healthy){throw '隔离版本启动健康未通过'}
Write-Output "隔离${Version}预演通过，正式服务未操作"
