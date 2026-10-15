$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
$directory=(Resolve-Path "$root/runtime/s69-r4-owned-db/data").Path
if($directory -ne "$root\runtime\s69-r4-owned-db\data"){throw '隔离目录不匹配'}
if(Get-NetTCPConnection -State Listen -LocalPort 55434 -ErrorAction SilentlyContinue){throw '55434已有监听，拒绝操作'}
$pg='D:/Program Files/PostgreSQL/18/bin'
$p=Start-Process "$pg/pg_ctl.exe" -ArgumentList @('-D',$directory,'-l',"$dest/isolated-postgres.log",'-o','"-p 55434 -h 127.0.0.1 -c max_connections=20 -c shared_buffers=32MB"','-w','start') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dest/db-start.log" -RedirectStandardError "$dest/db-start-error.log"
if(-not $p.WaitForExit(10000) -or $p.ExitCode -ne 0){throw '隔离控制器未成功退出，保留现场'}
. "$PSScriptRoot/load-local-env.ps1"
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{$identity=& "$pg/psql.exe" -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -v ON_ERROR_STOP=1 -c "SELECT json_build_object('port',inet_server_port(),'database',current_database(),'directory',current_setting('data_directory'),'started_at',pg_postmaster_start_time());";if($LASTEXITCODE -ne 0){throw '隔离身份查询失败'};$actual=$identity|ConvertFrom-Json;if($actual.port -ne 55434 -or $actual.database -ne 'huankan_r4_test' -or $actual.directory.Replace('/','\') -ne $directory){throw '隔离身份不匹配'};$actual|ConvertTo-Json|Set-Content "$dest/isolated-db-identity.json" -Encoding utf8}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
Write-Output '仅55434独占测试实例已核实；5432未操作。'
