$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
if(Get-NetTCPConnection -State Listen -LocalPort 55434 -ErrorAction SilentlyContinue){throw '55434已占用，不操作已有服务'}
$directory="$root/runtime/s69-r4-owned-db"
if(Test-Path $directory){throw '拒绝覆盖已有数据库目录'}
New-Item -ItemType Directory -Path $directory|Out-Null
$passwordFile="$directory/init-password.tmp"
try{
 [IO.File]::WriteAllText($passwordFile,$env:HUANKAN_TEST_DB_PASSWORD,[Text.UTF8Encoding]::new($false))
 & 'D:/Program Files/PostgreSQL/18/bin/initdb.exe' -D "$directory/data" -U $env:HUANKAN_TEST_DB_USER --encoding=UTF8 --locale=C --auth-host=scram-sha-256 --auth-local=scram-sha-256 --pwfile=$passwordFile > "$directory/init.log" 2>&1
 if($LASTEXITCODE -ne 0){throw '本轮独占实例初始化失败'}
}finally{Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue}
$control=Start-Process 'D:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -ArgumentList @('-D',"$directory/data",'-l',"$directory/postgres.log",'-o','"-p 55434 -h 127.0.0.1 -c max_connections=20 -c shared_buffers=32MB"','-w','start') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput "$directory/start.log" -RedirectStandardError "$directory/start-error.log"
if($control.ExitCode -ne 0){throw '本轮独占实例启动失败'}
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{
 & 'D:/Program Files/PostgreSQL/18/bin/createdb.exe' -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -w huankan_r4_test
 if($LASTEXITCODE -ne 0){throw '隔离库创建失败'}
 & 'D:/Program Files/PostgreSQL/18/bin/psql.exe' -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -c "SELECT json_build_object('database',current_database(),'port',inet_server_port(),'started_at',pg_postmaster_start_time(),'directory',current_setting('data_directory'))" | Set-Content "$root/docs/evidence/s69-r4-owned-db-identity.json" -Encoding utf8
 if($LASTEXITCODE -ne 0){throw '独占实例身份未验证'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
Write-Output '本轮新建独占55434实例已核实；未操作既有5432/55432服务'
