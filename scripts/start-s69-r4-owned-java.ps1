$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
if(Get-NetTCPConnection -State Listen -LocalPort 18088 -ErrorAction SilentlyContinue){throw '隔离18088已占用'}
$identity=Get-Content "$root/docs/evidence/s69-r4-owned-db-identity.json" -Raw|ConvertFrom-Json
if($identity.port -ne 55434 -or $identity.database -ne 'huankan_r4_test' -or $identity.directory.Replace('\','/') -ne "$root/runtime/s69-r4-owned-db/data".Replace('\','/')){throw '新独占数据库身份不匹配'}
$schema='r1_web_'+[guid]::NewGuid().ToString('N')
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try{& 'D:/Program Files/PostgreSQL/18/bin/psql.exe' -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -v ON_ERROR_STOP=1 -c "CREATE SCHEMA $schema"|Out-Null;if($LASTEXITCODE -ne 0){throw 'schema创建失败'}}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
$env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55434/huankan_r4_test?currentSchema=$schema";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN='isolated-s69-r1-test-token-32-characters';$env:HUANKAN_COLLECTOR_ENABLED='false'
$jar="$root/backend/target/huankan-s69-r3-candidate-0.1.0.jar"
if((Get-FileHash $jar).Hash.ToLower() -ne 'a579d4fd7c7f974b4a89532c97490f784446547409bec7d58dedffecaa721d61'){throw '候选包不匹配'}
$p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-jar',$jar,'--server.port=18088','--spring.datasource.hikari.maximum-pool-size=10','--spring.datasource.hikari.minimum-idle=10','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,metrics,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root/runtime/s69-r4-owned-candidate.log" -RedirectStandardError "$root/runtime/s69-r4-owned-candidate-error.log" -PassThru
@{database=$identity;schema=$schema;javaPid=$p.Id;javaCreatedAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');jar=$jar;jarSha256=(Get-FileHash $jar).Hash.ToLower()}|ConvertTo-Json -Depth 5|Set-Content "$root/runtime/s69-r4-owned-process.json" -Encoding utf8
Write-Output '本轮独占数据库候选Java已启动，未恢复正式服务'
