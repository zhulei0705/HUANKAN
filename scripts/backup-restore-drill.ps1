param([switch]$RestoreDrill)
$ErrorActionPreference='Stop'
. "$PSScriptRoot\load-local-env.ps1"
$root=Split-Path -Parent $PSScriptRoot
$pg='D:\Program Files\PostgreSQL\18\bin'
if($env:HUANKAN_DB_URL -ne 'jdbc:postgresql://127.0.0.1:5432/huankan'){throw '备份仅允许明确的寰瞰业务库'}
$directory=Join-Path $root ('runtime\backups\'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'))
New-Item -ItemType Directory -Path $directory | Out-Null
$dump=Join-Path $directory 'huankan.dump'
$env:PGPASSWORD=$env:HUANKAN_DB_PASSWORD
try{
 $before=& "$pg\psql.exe" -h 127.0.0.1 -p 5432 -U $env:HUANKAN_DB_USER -d huankan -w -At -v ON_ERROR_STOP=1 -c "SELECT json_build_object('count',count(*),'manifest',encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex')) FROM raw_evidence;"
 if($LASTEXITCODE -ne 0){throw '备份前证据清单查询失败'}
 $expected=$before|ConvertFrom-Json
 & "$pg\pg_dump.exe" -h 127.0.0.1 -p 5432 -U $env:HUANKAN_DB_USER -d huankan -w -Fc --no-owner --no-privileges -f $dump
 if($LASTEXITCODE -ne 0){throw '业务库备份失败'}
 $env:HUANKAN_BACKUP_DIRECTORY=$directory
 node "$PSScriptRoot\export-evidence-backup.mjs"
 if($LASTEXITCODE -ne 0){throw '原始证据导出失败'}
 $exported=Get-Content "$directory\evidence-manifest.json" -Raw|ConvertFrom-Json
 if($exported.count -ne $expected.count -or $exported.manifest -ne $expected.manifest){throw '备份期间业务证据发生变化，请在稳定窗口重试；本次保留备份但不声明一致性通过'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$report=@{checked_at=[DateTime]::UtcNow.ToString('o');directory=$directory;dump_sha256=(Get-FileHash $dump -Algorithm SHA256).Hash.ToLower();dump_bytes=(Get-Item $dump).Length;restore_attempted=$false;evidence_count=$expected.count;evidence_manifest=$expected.manifest;streaming_export=$true}
if($RestoreDrill){
 if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '恢复只允许已配置的独立55432测试实例'}
 $database='huankan_restore_'+[guid]::NewGuid().ToString('N')
 $env:PGPASSWORD=$env:HUANKAN_LOCAL_ADMIN_PASSWORD
 try{
  & "$pg\createdb.exe" -h 127.0.0.1 -p 55432 -U huankan_admin -w -O huankan_test $database
  if($LASTEXITCODE -ne 0){throw '隔离恢复库创建失败'}
  $env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
  & "$pg\pg_restore.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w --no-owner --no-privileges --exit-on-error $dump
  if($LASTEXITCODE -ne 0){throw '隔离恢复失败，保留恢复库用于排查'}
  $result=& "$pg\psql.exe" -h 127.0.0.1 -p 55432 -U huankan_test -d $database -w -At -v ON_ERROR_STOP=1 -c "SELECT json_build_object('evidence_count',(SELECT count(*) FROM raw_evidence),'outbox_count',(SELECT count(*) FROM stream_outbox),'hash_errors',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash<>encode(sha256(raw_content),'hex')),'broken_references',(SELECT count(*) FROM financial_event e LEFT JOIN raw_evidence r ON r.id=e.evidence_id WHERE r.id IS NULL)+(SELECT count(*) FROM stream_outbox o LEFT JOIN raw_evidence r ON r.id=o.evidence_id WHERE r.id IS NULL),'zip_hash_errors',(SELECT count(*) FROM gdelt_batch WHERE raw_zip IS NOT NULL AND sha256<>encode(sha256(raw_zip),'hex')),'manifest',(SELECT encode(sha256(convert_to(string_agg(id::text||raw_content_hash,',' ORDER BY id),'UTF8')),'hex') FROM raw_evidence));"
  if($LASTEXITCODE -ne 0){throw '恢复后验收查询失败'}
  $report.restore_attempted=$true;$report.restore_database=$database;$report.restored=($result|ConvertFrom-Json)
  if($report.restored.hash_errors -ne 0 -or $report.restored.broken_references -ne 0 -or $report.restored.zip_hash_errors -ne 0){throw '恢复后的哈希或引用不一致'}
  if($report.restored.evidence_count -ne $expected.count -or $report.restored.manifest -ne $expected.manifest){throw '恢复库与实际导出证据清单不一致；不声明恢复通过'}
  $report.restore_passed=$true
  # Successful drill database is retained; no recursive deletes or formal DB overwrite.
 }finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
}
$report|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 "$directory\backup-manifest.json"
$report|ConvertTo-Json -Depth 6|Set-Content -Encoding utf8 "$root\docs\evidence\s13-backup-restore.json"
Write-Output ($report|ConvertTo-Json -Depth 6)
