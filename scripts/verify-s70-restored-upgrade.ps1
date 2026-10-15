$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot\load-local-env.ps1"
$backup=Get-Content "$root\docs\evidence\s63-backup-restore.json" -Raw|ConvertFrom-Json
$database=$backup.restore_database
if(-not $backup.restore_passed -or $database -notmatch '^restore_s63_[a-f0-9]{32}$' -or $env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '恢复库隔离目标或备份状态无效'}
if(Get-NetTCPConnection -LocalPort 18085 -State Listen -ErrorAction SilentlyContinue){throw '18085已占用，未操作已有进程'}
$saved=@{url=$env:HUANKAN_DB_URL;user=$env:HUANKAN_DB_USER;password=$env:HUANKAN_DB_PASSWORD;enabled=$env:HUANKAN_COLLECTOR_ENABLED}
$p=$null
try{
 $env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55432/$database";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_COLLECTOR_ENABLED='false'
 $p=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList @('-jar',"$root\backend\target\huankan-s70-reviewed-0.1.0.jar",'--server.port=18085') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\s70-restored-upgrade.log" -RedirectStandardError "$root\runtime\s70-restored-upgrade-error.log" -PassThru
 $identity=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate
 $ready=$false
 for($i=0;$i -lt 90;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18085/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
 if(-not $ready){throw '恢复库升级实例未就绪'}
 $env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
 $result=& 'D:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w -At -v ON_ERROR_STOP=1 -c "SELECT json_build_object('checked_at',clock_timestamp(),'database',current_database(),'migrations',(SELECT count(*) FROM flyway_schema_history),'evidence',(SELECT count(*) FROM raw_evidence),'manifest',(SELECT encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex') FROM raw_evidence),'priority_table',to_regclass('research_priority_snapshot') IS NOT NULL,'lifecycle_table',to_regclass('event_research_activity') IS NOT NULL,'updates_table',to_regclass('watch_update') IS NOT NULL);"
 if($LASTEXITCODE -ne 0){throw '恢复库升级查询失败'}
 $report=$result|ConvertFrom-Json
 if($report.migrations -ne 51 -or $report.evidence -ne $backup.restored.evidence_count -or $report.manifest -ne $backup.restored.manifest){throw '新迁移恢复兼容性或原始证据身份检查失败'}
 $report|ConvertTo-Json -Depth 5|Set-Content "$root\docs\evidence\s70-restored-upgrade.json" -Encoding utf8
 Write-Output '真实全库恢复副本升级到V51通过，原始证据清单保持；不代表恢复了备份时间之后的新研究记录。'
}finally{
 if($p){$now=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)";if($now -and $now.CreationDate -eq $identity -and $now.CommandLine.Contains('--server.port=18085')){Stop-Process -Id $p.Id}}
 Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
 $env:HUANKAN_DB_URL=$saved.url;$env:HUANKAN_DB_USER=$saved.user;$env:HUANKAN_DB_PASSWORD=$saved.password;$env:HUANKAN_COLLECTOR_ENABLED=$saved.enabled
}
