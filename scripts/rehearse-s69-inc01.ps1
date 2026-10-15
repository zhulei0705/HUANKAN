# 仅隔离预演；没有正式启动路径，也不运行静态代理或浏览器。
param([switch]$ResumeOwnedDatabase)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$dest=Join-Path $root 'docs/evidence/s69-inc01/20261014T063813Z'
$dbDir=(Resolve-Path "$root/runtime/s69-r4-owned-db/data").Path
if($dbDir -ne "$root\runtime\s69-r4-owned-db\data"){throw '隔离数据目录不匹配'}
foreach($port in @(18088)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "隔离端口$port已占用，未操作任何进程"}}
if(-not $ResumeOwnedDatabase -and (Get-NetTCPConnection -State Listen -LocalPort 55434 -ErrorAction SilentlyContinue)){throw '55434已占用'}
if($ResumeOwnedDatabase){$owner=Get-CimInstance Win32_Process -Filter 'ProcessId=47568';if(-not $owner -or $owner.CreationDate.ToUniversalTime().Ticks -ne ([datetime]'2026-10-14T06:50:41.970358Z').ToUniversalTime().Ticks -or -not $owner.CommandLine.Contains('s69-r4-owned-db/data') -or -not $owner.CommandLine.Contains('55434')){throw '本轮隔离数据库身份变化，拒绝接续'}}
. "$PSScriptRoot/load-local-env.ps1"
$pg='D:/Program Files/PostgreSQL/18/bin'
$prior=Get-Content "$root/runtime/s69-r4-owned-process.json" -Raw|ConvertFrom-Json
if($prior.schema -notmatch '^r1_web_[a-f0-9]{32}$' -or $prior.database.port -ne 55434 -or $prior.database.database -ne 'huankan_r4_test'){throw '旧隔离记录不匹配'}
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
$env:PGCONNECT_TIMEOUT='5'
function Db([string]$q){$result=& "$pg/psql.exe" -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -v ON_ERROR_STOP=1 -c $q;if($LASTEXITCODE -ne 0){throw '隔离只读查询失败'};return ($result|ConvertFrom-Json)}
$started=[datetimeoffset]::Now.ToString('o')
if(-not $ResumeOwnedDatabase){
 $control=Start-Process "$pg/pg_ctl.exe" -ArgumentList @('-D',$dbDir,'-l',"$dest/isolated-postgres.log",'-o','"-p 55434 -h 127.0.0.1 -c max_connections=20 -c shared_buffers=32MB"','-w','start') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dest/isolated-db-start.txt" -RedirectStandardError "$dest/isolated-db-start-error.txt"
 if(-not $control.WaitForExit(10000) -or $control.ExitCode -ne 0){throw '隔离数据库控制器未成功退出，保留日志'}
}
$identity=Db "BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SELECT json_build_object('port',inet_server_port(),'database',current_database(),'directory',current_setting('data_directory'),'started_at',pg_postmaster_start_time()); COMMIT;"
if($identity.port -ne 55434 -or $identity.database -ne 'huankan_r4_test' -or $identity.directory.Replace('/','\') -ne $dbDir){throw '隔离实例身份核实失败'}
$rows=@()
$schema=$prior.schema
$env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55434/huankan_r4_test?currentSchema=$schema"
$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
$env:HUANKAN_ADMIN_TOKEN='isolated-inc01-token-32-characters-only';$env:HUANKAN_COLLECTOR_ENABLED='false'
$snapshotSql="BEGIN READ ONLY; SET LOCAL statement_timeout='3s'; SET LOCAL search_path=$schema; SELECT json_build_object('evidence',(SELECT json_agg(t) FROM(SELECT id,raw_content_hash,encode(sha256(raw_content),'hex') actual_hash FROM raw_evidence)t),'outbox',(SELECT json_agg(t) FROM(SELECT id,event_id,evidence_id FROM stream_outbox)t),'migrations',(SELECT json_agg(t) FROM(SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank)t),'jobs',(SELECT count(*) FROM ingestion_job)); COMMIT;"
$before=Db $snapshotSql
$before|ConvertTo-Json -Depth 12|Set-Content "$dest/isolated-before.json" -Encoding utf8
$versions=@(@{name='原版';jar='huankan-s70-reviewed-0.1.0.jar';hash='197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'},@{name='R3';jar='huankan-s69-r3-candidate-0.1.0.jar';hash='a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61'},@{name='回滚原版';jar='huankan-s70-reviewed-0.1.0.jar';hash='197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'})
$index=0
foreach($v in $versions){
 $index++;$jar="$root/backend/target/$($v.jar)"
 if((Get-FileHash $jar).Hash.ToLowerInvariant() -ne $v.hash){throw 'JAR哈希不匹配'}
 if(Get-NetTCPConnection -State Listen -LocalPort 18088 -ErrorAction SilentlyContinue){throw '18088被占用'}
 $at=[datetimeoffset]::Now.ToString('o')
 $p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Djavax.net.ssl.trustStoreType=Windows-ROOT','-jar',$jar,'--server.address=127.0.0.1','--server.port=18088','--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=1','--huankan.scheduling-enabled=false','--huankan.collector-enabled=false','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$dest/isolated-java-$index.log" -RedirectStandardError "$dest/isolated-java-$index-error.log" -PassThru
 $saved=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
 @{pid=$p.Id;createdAt=$saved.CreationDate.ToUniversalTime().ToString('o');jar=$jar;sha256=$v.hash;port=18088}|ConvertTo-Json|Set-Content "$dest/isolated-identity-$index.json" -Encoding utf8
 $healthy=$false
 for($n=0;$n -lt 60;$n++){try{$h=Invoke-RestMethod 'http://127.0.0.1:18088/actuator/health' -TimeoutSec 1;if($h.status -eq 'UP'){$healthy=$true;break}}catch{};if($p.HasExited){break};Start-Sleep -Milliseconds 500}
 if(-not $healthy){throw "隔离$($v.name)健康未通过；保留进程及现场，不强杀"}
 $listener=@(Get-NetTCPConnection -State Listen -LocalPort 18088)
 if(@($listener|Where-Object OwningProcess -ne $p.Id).Count -ne 0){throw '监听身份不匹配'}
 $data=Db $snapshotSql
 $record=@{version=$v.name;started=$at;healthyAt=[datetimeoffset]::Now.ToString('o');pid=$p.Id;hash=$v.hash;health=$h;database=$data;ports=@($listener|Select-Object LocalAddress,LocalPort,OwningProcess)}
 $record|ConvertTo-Json -Depth 12|Set-Content "$dest/isolated-stage-$index.json" -Encoding utf8
 $live=Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)"
 if($live.CreationDate.ToUniversalTime().Ticks -ne $saved.CreationDate.ToUniversalTime().Ticks -or -not $live.CommandLine.Contains($jar) -or -not $live.CommandLine.Contains('--server.port=18088')){throw '关闭前进程身份不匹配'}
 Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18088/actuator/shutdown' -Headers @{Authorization="Bearer $env:HUANKAN_ADMIN_TOKEN"} -ContentType 'application/json' -Body '{}' -TimeoutSec 5|Out-Null
 if(-not $p.WaitForExit(15000)){throw '隔离Java未正常关闭；保留现场，不强杀'}
 $record.closedAt=[datetimeoffset]::Now.ToString('o');$record.exitCode=$p.ExitCode;$rows+=$record
}
$after=Db $snapshotSql
$check=Db "BEGIN READ ONLY; SELECT json_build_object('port',inet_server_port(),'directory',current_setting('data_directory'),'started_at',pg_postmaster_start_time()); COMMIT;"
if($check.port -ne 55434 -or $check.directory -ne $identity.directory -or $check.started_at -ne $identity.started_at){throw '停止前隔离数据库身份不匹配'}
& "$pg/pg_ctl.exe" -D $dbDir -w -m fast stop > "$dest/isolated-db-stop.txt" 2>&1
if($LASTEXITCODE -ne 0){throw '隔离数据库未正常停止'}
@{started=$started;finished=[datetimeoffset]::Now.ToString('o');database=$identity;schema=$schema;before=$before;stages=$rows;after=$after;preserved=(($before|ConvertTo-Json -Depth 12 -Compress) -eq ($after|ConvertTo-Json -Depth 12 -Compress));databaseStopped=$true;formalTouched=$false;browserRun=$false}|ConvertTo-Json -Depth 16|Set-Content "$dest/isolated-rehearsal.json" -Encoding utf8
Write-Output '隔离三阶段预演结束；正式服务未操作。'
