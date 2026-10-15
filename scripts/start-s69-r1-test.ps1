$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '测试实例配置不匹配'}
foreach($port in @(18088,5184)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "隔离端口$port已占用，未操作已有进程"}}
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{
 $identity=& 'D:/Program Files/PostgreSQL/18/bin/psql.exe' -h 127.0.0.1 -p 55432 -U $env:HUANKAN_TEST_DB_USER -d huankan_test -w -Atq -c "SELECT json_build_object('database',current_database(),'port',inet_server_port(),'user',current_user,'started_at',pg_postmaster_start_time())"
 if($LASTEXITCODE -ne 0){throw '测试实例身份查询失败'}
 $actual=$identity|ConvertFrom-Json
 if($actual.database -ne 'huankan_test' -or $actual.port -ne 55432){throw '实际数据库身份不匹配'}
 $schema='r1_web_'+[guid]::NewGuid().ToString('N')
 & 'D:/Program Files/PostgreSQL/18/bin/psql.exe' -h 127.0.0.1 -p 55432 -U $env:HUANKAN_TEST_DB_USER -d huankan_test -w -v ON_ERROR_STOP=1 -c "CREATE SCHEMA $schema" | Out-Null
 if($LASTEXITCODE -ne 0){throw '独立schema创建失败'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$candidate=Get-Content -Raw "$root/runtime/s69-r1-candidate.json"|ConvertFrom-Json
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
$names=@('HUANKAN_DB_URL','HUANKAN_DB_USER','HUANKAN_DB_PASSWORD','HUANKAN_ADMIN_TOKEN','HUANKAN_COLLECTOR_ENABLED','HUANKAN_BACKEND_URL')
$saved=@{};foreach($name in $names){$saved[$name]=[Environment]::GetEnvironmentVariable($name,'Process')}
try{
 $env:HUANKAN_DB_URL="$($env:HUANKAN_TEST_DB_URL)?currentSchema=$schema";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
 $env:HUANKAN_ADMIN_TOKEN='isolated-s69-r1-test-token-32-characters';$env:HUANKAN_COLLECTOR_ENABLED='false';$env:HUANKAN_BACKEND_URL='http://127.0.0.1:18088'
 $jar="$root/backend/target/huankan-s69-r1-candidate-0.1.0.jar"
 if(-not(Test-Path -LiteralPath $jar)){throw '尚未完成R1后端完整构建'}
 $java=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-jar',$jar,'--server.port=18088','--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=1','--spring.datasource.hikari.connection-timeout=500','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s69-r1-jvm-$stamp.log" -RedirectStandardError "$root/runtime/s69-r1-jvm-$stamp-error.log" -PassThru
 $static=Start-Process (Get-Command node.exe).Source -ArgumentList @("$root/scripts/serve-s69-static.mjs",$candidate.release,'5184',$candidate.manifest_sha256) -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s69-r1-static-isolated-$stamp.log" -RedirectStandardError "$root/runtime/s69-r1-static-isolated-$stamp-error.log" -PassThru
 $info=@{startedAt=(Get-Date).ToUniversalTime().ToString('o');schema=$schema;stamp=$stamp;database=$actual;jar=$jar;jarSha256=(Get-FileHash -LiteralPath $jar).Hash;candidate=$candidate;javaPid=$java.Id;javaCreatedAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($java.Id)").CreationDate.ToUniversalTime().ToString('o');staticPid=$static.Id;staticCreatedAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($static.Id)").CreationDate.ToUniversalTime().ToString('o')}
 $info|ConvertTo-Json -Depth 6|Set-Content "$root/runtime/s69-r1-test-process.json" -Encoding utf8
}finally{foreach($name in $names){[Environment]::SetEnvironmentVariable($name,$saved[$name],'Process')}}
Write-Output "R1隔离Java18088和静态5184已启动；仅使用55432的本次schema=$schema，未操作正式实例。"
