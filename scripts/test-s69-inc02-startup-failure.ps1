$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
if(Get-NetTCPConnection -State Listen -LocalPort 18088 -ErrorAction SilentlyContinue){throw '18088尚有监听，不执行失败启动'}
. "$PSScriptRoot/load-local-env.ps1"
$info=Get-Content "$dest/isolation-r3.json" -Raw|ConvertFrom-Json
$env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55434/huankan_r4_test?currentSchema=$($info.schema)";$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN='short';$env:HUANKAN_COLLECTOR_ENABLED='false'
$pg='D:/Program Files/PostgreSQL/18/bin/psql.exe';$env:PGPASSWORD=$env:HUANKAN_TEST_DB_PASSWORD
function Snapshot { $sql="BEGIN READ ONLY; SET LOCAL search_path=$($info.schema); SELECT json_build_object('jobs',(SELECT count(*) FROM ingestion_job),'raw',(SELECT count(*) FROM raw_evidence),'events',(SELECT count(*) FROM financial_event),'outbox',(SELECT count(*) FROM stream_outbox)); COMMIT;";$r=& $pg -h 127.0.0.1 -p 55434 -U $env:HUANKAN_TEST_DB_USER -d huankan_r4_test -w -Atq -v ON_ERROR_STOP=1 -c $sql;if($LASTEXITCODE -ne 0){throw '失败启动前后快照失败'};return $r|ConvertFrom-Json }
try{
 $before=Snapshot
 $p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-Dhuankan.process.purpose=inc02-isolated-startup-failure','-jar',"$root/backend/target/huankan-s70-reviewed-0.1.0.jar",'--server.address=127.0.0.1','--server.port=18088') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dest/startup-failure-java.log" -RedirectStandardError "$dest/startup-failure-java-error.log"
 if(-not $p.WaitForExit(30000)){throw '预期失败进程未退出，保留现场，不强杀'}
 $after=Snapshot;$log=Get-Content "$dest/startup-failure-java.log" -Raw
 $errorLog=Get-Content "$dest/startup-failure-java-error.log" -Raw
 $passed=($p.ExitCode -ne 0 -and ($log+$errorLog) -match '管理密钥至少32个字符' -and ($before|ConvertTo-Json -Compress) -eq ($after|ConvertTo-Json -Compress))
 @{at=[datetimeoffset]::Now.ToString('o');pid=$p.Id;exitCode=$p.ExitCode;before=$before;after=$after;passed=$passed;tests=@{realFailureLogged=(($log+$errorLog) -match '管理密钥至少32个字符');nonzeroExit=($p.ExitCode -ne 0);businessStateUnchanged=(($before|ConvertTo-Json -Compress) -eq ($after|ConvertTo-Json -Compress))};formalTouched=$false}|ConvertTo-Json -Depth 6|Set-Content "$dest/startup-failure.json" -Encoding utf8
 if(-not $passed){throw '启动失败保护验收未通过'}
}finally{Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue}
