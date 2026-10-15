$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
if($env:HUANKAN_TEST_DB_URL -ne 'jdbc:postgresql://127.0.0.1:55432/huankan_test'){throw '仅允许本项目独立测试库'}
if(Get-NetTCPConnection -State Listen -LocalPort 18084 -ErrorAction SilentlyContinue){throw '18084已占用，未操作其他进程'}
$schema='web_it_'+[guid]::NewGuid().ToString('N')
$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
try {& 'D:\Program Files\PostgreSQL\18\bin\psql.exe' -h 127.0.0.1 -p 55432 -U huankan_test -d huankan_test -w -v ON_ERROR_STOP=1 -c "CREATE SCHEMA $schema";if($LASTEXITCODE -ne 0){throw '隔离schema创建失败'}} finally {Remove-Item Env:PGPASSWORD}
$saved=@{url=$env:HUANKAN_DB_URL;user=$env:HUANKAN_DB_USER;password=$env:HUANKAN_DB_PASSWORD;token=$env:HUANKAN_ADMIN_TOKEN;enabled=$env:HUANKAN_COLLECTOR_ENABLED}
try {
 $env:HUANKAN_DB_URL="$($env:HUANKAN_TEST_DB_URL)?currentSchema=$schema";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN='isolated-s65-web-test-token-32-characters';$env:HUANKAN_COLLECTOR_ENABLED='false'
 $jar=Join-Path $root 'backend\target\huankan-s65-accepted-0.1.0.jar'
 $process=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList @('-jar',$jar,'--server.port=18084') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\isolated-s65-web.log" -RedirectStandardError "$root\runtime\isolated-s65-web-error.log" -PassThru
 $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
 @{schema=$schema;processId=$process.Id;createdAt=$info.CreationDate.ToUniversalTime().ToString('o')}|ConvertTo-Json|Set-Content "$root\runtime\isolated-s65-web-process.json"
}finally{$env:HUANKAN_DB_URL=$saved.url;$env:HUANKAN_DB_USER=$saved.user;$env:HUANKAN_DB_PASSWORD=$saved.password;$env:HUANKAN_ADMIN_TOKEN=$saved.token;$env:HUANKAN_COLLECTOR_ENABLED=$saved.enabled}
$env:HUANKAN_WEB_TEST_SCHEMA=$schema
$ready=$false
for($i=0;$i -lt 30;$i++){try{if((Invoke-RestMethod http://127.0.0.1:18084/actuator/health -TimeoutSec 2).status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if(-not $ready){throw '隔离Web后端健康检查失败'}
Write-Output "隔离Web后端就绪，测试schema=$schema，后台采集关闭。"




