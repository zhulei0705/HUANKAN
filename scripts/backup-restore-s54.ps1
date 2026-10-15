$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot\load-local-env.ps1"
if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '仅允许本项目独立测试实例'}
$pg='D:\Program Files\PostgreSQL\18\bin'
$directory=Join-Path $root ('runtime\backups\s54-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $directory|Out-Null
$database='restore_s54_'+[guid]::NewGuid().ToString('N')
$report=@{started_at=[DateTime]::UtcNow.ToString('o');restore_database=$database;directory=$directory;restore_passed=$false;formal_database_changed=$false;snapshot_note='pg_dump一致快照；校验冻结恢复库，不以采集仍在增长的线上计数替代同一快照';raw_export_note='原始证据bytea包含在全库备份，不重复导出第二份文件'}
try{
 $env:PGPASSWORD=$env:HUANKAN_DB_PASSWORD
 & "$pg\pg_dump.exe" -h 127.0.0.1 -p 5432 -U $env:HUANKAN_DB_USER -d huankan -w -Fc '--compress=1' --lock-wait-timeout=5000 -f "$directory\huankan.dump"
 if($LASTEXITCODE -ne 0){throw '正式库一致备份失败，未执行恢复'}
 $report.dump_sha256=(Get-FileHash "$directory\huankan.dump" -Algorithm SHA256).Hash.ToLower();$report.dump_bytes=(Get-Item "$directory\huankan.dump").Length
 $env:PGPASSWORD=$env:HUANKAN_LOCAL_ADMIN_PASSWORD
 & "$pg\createdb.exe" -h 127.0.0.1 -p 55432 -U huankan_admin -w -O huankan_test $database
 if($LASTEXITCODE -ne 0){throw '专用恢复库创建失败'}
 $env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
 & "$pg\pg_restore.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w --no-owner --no-privileges --exit-on-error "$directory\huankan.dump"
 if($LASTEXITCODE -ne 0){throw '隔离恢复失败，保留现场'}
 $result=& "$pg\psql.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w -At -v ON_ERROR_STOP=1 -c "SELECT json_build_object('migrations',(SELECT count(*) FROM flyway_schema_history),'evidence_count',(SELECT count(*) FROM raw_evidence),'outbox_count',(SELECT count(*) FROM stream_outbox),'hash_errors',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')),'resource_hash_errors',(SELECT count(*) FROM official_resource_response WHERE raw_content IS NOT NULL AND sha256<>encode(sha256(raw_content),'hex')),'broken_references',(SELECT count(*) FROM financial_event e LEFT JOIN raw_evidence r ON r.id=e.evidence_id WHERE r.id IS NULL)+(SELECT count(*) FROM stream_outbox o LEFT JOIN raw_evidence r ON r.id=o.evidence_id WHERE r.id IS NULL),'zip_hash_errors',(SELECT count(*) FROM gdelt_batch WHERE raw_zip IS NOT NULL AND sha256<>encode(sha256(raw_zip),'hex')),'brief_count',(SELECT count(*) FROM financial_brief),'calendar_count',(SELECT count(*) FROM official_release_calendar),'sec_xbrl_count',(SELECT count(*) FROM sec_xbrl_fact),'manifest',(SELECT encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex') FROM raw_evidence));"
 if($LASTEXITCODE -ne 0){throw '恢复查询失败'}
 $report.restored=$result|ConvertFrom-Json
 if($report.restored.migrations -lt 48 -or $report.restored.hash_errors -ne 0 -or $report.restored.resource_hash_errors -ne 0 -or $report.restored.broken_references -ne 0 -or $report.restored.zip_hash_errors -ne 0){throw '恢复结构或真实性检查失败'}
 $report.restore_passed=$true
}catch{$report.error=$_.Exception.Message;throw}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue;$report.ended_at=[DateTime]::UtcNow.ToString('o');$report|ConvertTo-Json -Depth 8|Set-Content "$root\docs\evidence\s54-backup-restore.json" -Encoding utf8}
$report|ConvertTo-Json -Depth 8
