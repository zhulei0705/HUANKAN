param([switch]$RestartBackend,[switch]$StopBackend)
$ErrorActionPreference='Stop'
$root=Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\load-local-env.ps1"
if(-not $env:HUANKAN_ADMIN_TOKEN){throw '管理密钥未配置'}
New-Item -ItemType Directory -Force "$root\runtime" | Out-Null
$jar=Join-Path $root 'backend\target\huankan-0.1.0.jar'
$pidFile=Join-Path $root 'runtime\backend-process.json'
if(($RestartBackend -or $StopBackend) -and (Test-Path $pidFile)) {
    $saved=Get-Content $pidFile -Raw | ConvertFrom-Json
    $existing=Get-CimInstance Win32_Process -Filter "ProcessId=$($saved.processId)"
    if($existing -and $existing.CommandLine.Contains($jar) -and $existing.CreationDate.ToUniversalTime().Ticks -eq ([datetime]$saved.createdAt).ToUniversalTime().Ticks) { Stop-Process -Id $saved.processId; Start-Sleep -Milliseconds 500 }
    elseif($existing) {throw '进程标识不匹配，拒绝停止其他进程'}
}
if($StopBackend){Write-Output '仅已停止经路径和创建时间核对的寰瞰后端。';return}
$backendListeners=Get-NetTCPConnection -State Listen -LocalPort 18080 -ErrorAction SilentlyContinue
if($backendListeners) {
    foreach($listener in $backendListeners){$info=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)";if(-not $info.CommandLine.Contains($jar)){throw '18080已被其他进程占用；未操作其他进程'}}
}
if(-not $backendListeners) {
    if(-not $env:HUANKAN_COLLECTOR_ENABLED){$env:HUANKAN_COLLECTOR_ENABLED='true'}
    $process=Start-Process -FilePath 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe' -ArgumentList @('-Djava.net.preferIPv4Stack=true','-Djavax.net.ssl.trustStoreType=Windows-ROOT','-Djavax.net.ssl.trustStore=NONE','-jar',$jar,'--server.port=18080') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\backend.log" -RedirectStandardError "$root\runtime\backend-error.log" -PassThru
    $info=Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
    @{processId=$process.Id;createdAt=$info.CreationDate.ToUniversalTime().ToString('o');jar=$jar}|ConvertTo-Json|Set-Content $pidFile
}
if(-not (Get-NetTCPConnection -State Listen -LocalPort 5173 -ErrorAction SilentlyContinue)) {
    $node=(Get-Command node.exe).Source
    $vite=Join-Path $root 'frontend\node_modules\vite\bin\vite.js'
    Start-Process -FilePath $node -ArgumentList @($vite,'--host','127.0.0.1','--port','5173','--strictPort') -WorkingDirectory "$root\frontend" -WindowStyle Hidden -RedirectStandardOutput "$root\runtime\frontend.log" -RedirectStandardError "$root\runtime\frontend-error.log" | Out-Null
}
Write-Output '寰瞰后端端口18080，前端5173；后台日志在runtime，未打印凭证。'
