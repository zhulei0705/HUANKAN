$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
if(Get-NetTCPConnection -State Listen -LocalPort 18088 -ErrorAction SilentlyContinue){throw '隔离Java仍监听，禁止停止数据库'}
. "$PSScriptRoot/load-local-env.ps1"
$before=Get-Content "$dest/isolated-db-identity.json" -Raw|ConvertFrom-Json
$pg='D:/Program Files/PostgreSQL/18/bin';$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{
 $text=& "$pg/psql.exe" -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -v ON_ERROR_STOP=1 -c "SELECT json_build_object('port',inet_server_port(),'database',current_database(),'directory',current_setting('data_directory'),'started_at',pg_postmaster_start_time());"
 if($LASTEXITCODE -ne 0){throw '停止前身份查询失败'};$actual=$text|ConvertFrom-Json
 foreach($key in @('port','database','directory','started_at')){if($actual.$key -ne $before.$key){throw "隔离数据库$key变化"}}
 $info=Get-Content "$dest/isolation-r3.json" -Raw|ConvertFrom-Json
 & "$pg/pg_dump.exe" -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w --schema $info.schema --no-owner --no-privileges -f "$dest/final-isolated-schema.sql"
 if($LASTEXITCODE -ne 0){throw '隔离schema导出失败，保留实例'}
 $r=& "$pg/psql.exe" -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -v ON_ERROR_STOP=1 -c "BEGIN READ ONLY; SET LOCAL search_path=$($info.schema); SELECT json_build_object('raw',(SELECT count(*) FROM raw_evidence),'events',(SELECT count(*) FROM financial_event),'outbox',(SELECT count(*) FROM stream_outbox),'valid_raw',(SELECT count(*) FROM raw_evidence WHERE raw_content_hash=encode(sha256(raw_content),'hex')),'migrations',(SELECT count(*) FROM flyway_schema_history WHERE success),'job_audits',(SELECT count(*) FROM audit_log WHERE actor='HUANKAN-S69-INC02')); COMMIT;"
 if($LASTEXITCODE -ne 0){throw '隔离最终数据快照失败'};$data=$r|ConvertFrom-Json
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$dir=(Resolve-Path $actual.directory).Path
if($dir -ne "$root\runtime\s69-r4-owned-db\data"){throw '隔离停止目录越界'}
& "$pg/pg_ctl.exe" -D $dir -w -m fast stop > "$dest/db-stop.log" 2>&1
if($LASTEXITCODE -ne 0){throw '隔离数据库未正常停止'}
$ports=@(Get-NetTCPConnection -State Listen|Where-Object LocalPort -in @(5432,55434,18080,18087,18088,5173))
@{at=[datetimeoffset]::Now.ToString('o');database=$actual;finalData=$data;isolatedDatabaseStopped=$true;schemaDump='final-isolated-schema.sql';formalDbTouched=$false;ports=@($ports|Select-Object LocalAddress,LocalPort,OwningProcess);processes=@(Get-CimInstance Win32_Process|Where-Object ProcessId -in @(7072,50392,54388,59328,73024)|Select-Object ProcessId,ParentProcessId,CreationDate)}|ConvertTo-Json -Depth 7|Set-Content "$dest/final-state.json" -Encoding utf8
Write-Output '本轮隔离Java/数据库均已关闭，测试数据和导出保留，5432与BizPulse未操作。'
