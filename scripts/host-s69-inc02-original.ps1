# 后续拟由任务计划程序运行的监督器；本轮只运行Isolated。不会注册或启用系统任务。
param([ValidateSet('Isolated','Formal')][string]$Mode='Isolated',[string]$IsolationFile,[string]$RunName='original-run',[switch]$ExplicitUserApproved)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
if($RunName -notmatch '^[a-z0-9-]+$'){throw '运行记录名称无效'}
if($Mode -eq 'Formal'){
 $gate=Get-Content "$dest/recovery-gate.json" -Raw|ConvertFrom-Json
 if($gate.originalRecovery -ne 'Go' -or -not $ExplicitUserApproved -or -not $gate.hostValidated -or -not $gate.formalWriteApproval){throw 'INC02正式恢复门禁未准入；未启动任何正式进程'}
 throw '正式托管注册/独立性及当前授权需后续另行验收；本轮不开放Formal启动分支'
}
$info=Get-Content -LiteralPath $IsolationFile -Raw|ConvertFrom-Json
if($info.port -ne 55434 -or $info.database -ne 'huankan_r4_test' -or $info.schema -notmatch '^inc02_[a-f0-9]{32}$'){throw '隔离配置不匹配'}
if(Get-NetTCPConnection -State Listen -LocalPort 18088 -ErrorAction SilentlyContinue){throw '18088占用，不操作已有进程'}
. "$PSScriptRoot/load-local-env.ps1"
if(-not $env:HUANKAN_INC02_TEST_TOKEN){throw '隔离专用令牌缺失'}
$jar=(Resolve-Path "$root/backend/target/huankan-s70-reviewed-0.1.0.jar").Path
if((Get-FileHash $jar).Hash.ToLowerInvariant() -ne '197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'){throw '原版JAR哈希不匹配'}
$env:HUANKAN_DB_URL="jdbc:postgresql://127.0.0.1:55434/huankan_r4_test?currentSchema=$($info.schema)"
$env:HUANKAN_DB_USER=$env:HUANKAN_TEST_DB_USER;$env:HUANKAN_DB_PASSWORD=$env:HUANKAN_TEST_DB_PASSWORD;$env:HUANKAN_ADMIN_TOKEN=$env:HUANKAN_INC02_TEST_TOKEN;$env:HUANKAN_COLLECTOR_ENABLED='false'
Import-Module "$PSScriptRoot/inc02-process-identity.psm1" -Force
$record=@{startedAt=[datetimeoffset]::Now.ToString('o');mode='Isolated';supervisorPid=$PID;database=$info;initialHealth='待实测';autoRestart=$false}
$record|ConvertTo-Json -Depth 6|Set-Content "$dest/$RunName-supervisor.json" -Encoding utf8
$p=Start-Process 'C:/Program Files/Java/jdk-21.0.10/bin/java.exe' -ArgumentList @('-Dfile.encoding=UTF-8','-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Dcom.sun.management.jmxremote','-Dhuankan.process.purpose=inc02-isolated-original','-jar',$jar,'--server.address=127.0.0.1','--server.port=18088','--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=1','--spring.datasource.hikari.register-mbeans=true','--huankan.collector-enabled=false','--management.endpoint.shutdown.enabled=true','--management.endpoints.web.exposure.include=health,shutdown','--server.shutdown=graceful') -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$dest/$RunName-java.log" -RedirectStandardError "$dest/$RunName-java-error.log"
$ready=$false
for($n=0;$n -lt 60;$n++){if($p.HasExited){break};try{$h=Invoke-RestMethod 'http://127.0.0.1:18088/actuator/health' -TimeoutSec 1;if($h.status -eq 'UP'){$ready=$true;break}}catch{};Start-Sleep -Milliseconds 500}
if($ready){
 $identity=Get-Inc02ProcessIdentity -ProcessId $p.Id -Port 18088
 $identity|ConvertTo-Json -Depth 6|Set-Content "$dest/$RunName-identity.json" -Encoding utf8
 $record.initialHealth='UP';$record.healthyAt=[datetimeoffset]::Now.ToString('o');$record.identity=$identity
}else{
 $record.initialHealth='FAILED';$record.failure='原版启动未就绪，查看实际stdout/stderr；未强杀、未自动重启'
}
$record|ConvertTo-Json -Depth 8|Set-Content "$dest/$RunName-supervisor-ready.json" -Encoding utf8
if(-not $ready -and -not $p.HasExited){throw '启动未就绪，保留现场等待人工；不强杀'}
# 等待Java本身，不使用Start-Process -Wait对子孙树的等待语义。
$p.WaitForExit();$record.exitedAt=[datetimeoffset]::Now.ToString('o');$record.exitCode=$p.ExitCode
$record|ConvertTo-Json -Depth 8|Set-Content "$dest/$RunName-exit.json" -Encoding utf8
exit $p.ExitCode
