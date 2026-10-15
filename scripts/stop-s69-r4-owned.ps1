$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$entry=Get-Content "$root/runtime/s69-r4-owned-process.json" -Raw|ConvertFrom-Json
$directory=(Resolve-Path "$root/runtime/s69-r4-owned-db/data").Path
if($entry.database.port -ne 55434 -or $entry.database.database -ne 'huankan_r4_test' -or $directory -ne "$root\runtime\s69-r4-owned-db\data" -or $entry.schema -notmatch '^r1_web_[a-f0-9]{32}$'){throw '独占隔离目标不匹配'}
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{
 $actual=& 'D:/Program Files/PostgreSQL/18/bin/psql.exe' -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -c "SELECT json_build_object('port',inet_server_port(),'directory',current_setting('data_directory'),'started_at',pg_postmaster_start_time())"
 if($LASTEXITCODE -ne 0){throw '实际实例核实失败'}
 $identity=$actual|ConvertFrom-Json
 if($identity.directory.Replace('/','\') -ne $directory -or $identity.port -ne 55434 -or $identity.started_at -ne $entry.database.started_at){throw '实际实例身份变化'}
 & 'D:/Program Files/PostgreSQL/18/bin/pg_dump.exe' -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w --schema $entry.schema --no-owner --no-privileges -f "$root/docs/evidence/s69-r4-owned-final-schema.sql"
 if($LASTEXITCODE -ne 0){throw '隔离导出失败'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$live=Get-CimInstance Win32_Process -Filter "ProcessId=$($entry.javaPid)"
if(-not $live -or $live.CreationDate.ToUniversalTime().Ticks -ne ([datetime]$entry.javaCreatedAt).ToUniversalTime().Ticks -or -not $live.CommandLine.Contains($entry.jar) -or -not $live.CommandLine.Contains('--server.port=18088')){throw '隔离Java身份变化'}
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:18088/actuator/shutdown -Headers @{Authorization='Bearer isolated-s69-r1-test-token-32-characters'} -ContentType 'application/json' -Body '{}' -TimeoutSec 5|Out-Null
for($n=0;$n -lt 100;$n++){if(-not(Get-Process -Id $entry.javaPid -ErrorAction SilentlyContinue)){break};Start-Sleep -Milliseconds 300}
if(Get-Process -Id $entry.javaPid -ErrorAction SilentlyContinue){throw '隔离Java未正常关闭，保留现场'}
& 'D:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D $directory -w -m fast stop > "$root/runtime/s69-r4-owned-db-stop.log" 2>&1
if($LASTEXITCODE -ne 0){throw '隔离数据库未正常停止'}
@{at=(Get-Date).ToUniversalTime().ToString('o');javaPid=$entry.javaPid;database=$identity;dump='docs/evidence/s69-r4-owned-final-schema.sql';data_directory_retained=$true;formal_touched=$false;old_55432_touched=$false}|ConvertTo-Json -Depth 5|Set-Content "$root/docs/evidence/s69-r4-owned-cleanup.json" -Encoding utf8
