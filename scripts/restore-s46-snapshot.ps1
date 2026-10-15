param([Parameter(Mandatory=$true)][string]$DumpPath)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot\load-local-env.ps1"
if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '仅允许独立测试实例'}
$dump=(Resolve-Path -LiteralPath $DumpPath).Path
if(-not $dump.StartsWith((Join-Path $root 'runtime\backups\'),[StringComparison]::OrdinalIgnoreCase) -or (Split-Path $dump -Leaf) -ne 'huankan.dump'){throw '仅允许项目自有备份文件'}
$pg='D:\Program Files\PostgreSQL\18\bin'
$database='restore_s46_'+[guid]::NewGuid().ToString('N')
$directory=Join-Path $root ('runtime\backups\s46-frozen-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $directory | Out-Null
$report=@{started_at=[DateTime]::UtcNow.ToString('o');dump=$dump;dump_sha256=(Get-FileHash -LiteralPath $dump -Algorithm SHA256).Hash.ToLower();restore_database=$database;formal_database_changed=$false;restore_passed=$false}
try{
 $env:PGPASSWORD=$env:HUANKAN_LOCAL_ADMIN_PASSWORD
 & "$pg\createdb.exe" -h 127.0.0.1 -p 55432 -U huankan_admin -w -O huankan_test $database
 if($LASTEXITCODE -ne 0){throw '独立恢复库创建失败'}
 $env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
 & "$pg\pg_restore.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w --no-owner --no-privileges --exit-on-error $dump
 if($LASTEXITCODE -ne 0){throw '隔离恢复失败，保留恢复库供核查'}
 $result=& "$pg\psql.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w -At -v ON_ERROR_STOP=1 -c "SELECT json_build_object('migrations',(SELECT count(*) FROM flyway_schema_history),'evidence_count',(SELECT count(*) FROM raw_evidence),'outbox_count',(SELECT count(*) FROM stream_outbox),'hash_errors',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')),'broken_references',(SELECT count(*) FROM financial_event e LEFT JOIN raw_evidence r ON r.id=e.evidence_id WHERE r.id IS NULL)+(SELECT count(*) FROM stream_outbox o LEFT JOIN raw_evidence r ON r.id=o.evidence_id WHERE r.id IS NULL),'zip_hash_errors',(SELECT count(*) FROM gdelt_batch WHERE raw_zip IS NOT NULL AND sha256<>encode(sha256(raw_zip),'hex')),'manifest',(SELECT encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex') FROM raw_evidence));"
 if($LASTEXITCODE -ne 0){throw '恢复库查询失败'}
 $report.restored=$result|ConvertFrom-Json
 $env:HUANKAN_RESTORE_DATABASE=$database;$env:HUANKAN_BACKUP_DIRECTORY=$directory
 node "$PSScriptRoot\export-s46-restored-evidence.mjs"
 if($LASTEXITCODE -ne 0){throw '冻结恢复库证据导出失败'}
 $export=Get-Content "$directory\evidence-manifest.json" -Raw|ConvertFrom-Json
 if($export.count -ne $report.restored.evidence_count -or $export.manifest -ne $report.restored.manifest -or $report.restored.hash_errors -ne 0 -or $report.restored.broken_references -ne 0 -or $report.restored.zip_hash_errors -ne 0){throw '冻结备份与恢复证据不一致'}
 $report.frozen_export=$directory;$report.restore_passed=$true
}catch{$report.error=$_.Exception.Message;throw}finally{
 Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
 $report.ended_at=[DateTime]::UtcNow.ToString('o');$report|ConvertTo-Json -Depth 8|Set-Content -Encoding utf8 "$root\docs\evidence\s46-frozen-restore.json"
}
$report|ConvertTo-Json -Depth 8
