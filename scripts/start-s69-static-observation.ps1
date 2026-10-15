param([Parameter(Mandatory=$true)][string]$FinalAudit,[Parameter(Mandatory=$true)][string]$TestReport,[switch]$UserConfirmed)
$ErrorActionPreference='Stop'
if(-not $UserConfirmed){throw '用户尚未明确确认正式静态切换，本轮仅准备方案'}
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
. "$PSScriptRoot/load-local-env.ps1"
$audit=Get-Content -LiteralPath (Join-Path $root $FinalAudit) -Raw|ConvertFrom-Json
$test=Get-Content -LiteralPath (Join-Path $root $TestReport) -Raw|ConvertFrom-Json
if(-not $audit.final -or -not $test.passed -or $test.results.Count -ne 10){throw '原窗口尚未冻结或隔离测试未通过，禁止启动正式静态入口'}
if(Get-NetTCPConnection -State Listen -LocalPort 5183 -ErrorAction SilentlyContinue){throw '5183已占用，不操作已有进程'}
$release=$test.candidate.release;$manifest=$test.candidate.manifest_sha256
$versionLog="$root/runtime/s69-static-observation-$($test.candidate.stamp).log"
if((Test-Path -LiteralPath $versionLog) -or (Test-Path -LiteralPath "$versionLog.error")){throw '该版本启动日志已存在，拒绝覆盖'}
$p=Start-Process (Get-Command node.exe).Source -ArgumentList @("$root/scripts/serve-s69-static.mjs",$release,'5183',$manifest,(Join-Path $root $FinalAudit),'--user-confirmed') -WorkingDirectory $root -WindowStyle Hidden -RedirectStandardOutput $versionLog -RedirectStandardError "$versionLog.error" -PassThru
@{processId=$p.Id;createdAt=(Get-CimInstance Win32_Process -Filter "ProcessId=$($p.Id)").CreationDate.ToUniversalTime().ToString('o');port=5183;release=$release;manifestSha256=$manifest;finalAudit=$FinalAudit;testReport=$TestReport}|ConvertTo-Json|Set-Content "$root/runtime/s69-static-observation-$($test.candidate.stamp)-process.json" -Encoding utf8
Write-Output '正式静态入口5183已安排启动，仅使用冻结前端资源；没有启动任何24小时观察、重启Java或操作PostgreSQL。实际就绪须核对启动日志及HTTP。'
