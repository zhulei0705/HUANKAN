# 待批准正式正常停止入口。默认仅核验；本轮没有正式停止授权，也没有正式进程。
param([Parameter(Mandatory)][string]$IdentityPath,[switch]$Execute,[switch]$UserApproved)
$ErrorActionPreference='Stop'
Import-Module "$PSScriptRoot/inc02-process-identity.psm1" -Force
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$saved=Get-Content -LiteralPath $IdentityPath -Raw|ConvertFrom-Json
if($saved.purpose -ne 'formal-original' -or $saved.port -ne 18080 -or $saved.jarSha256 -ne '197022ff189d8b29816cbc06df1591c7694bd068d0da1134326a524da1af2e77'){throw '正式原版身份范围不匹配'}
if($saved.account -ne 'DESKTOP-JMS8B0H\sthl' -or $saved.java -ne 'C:\Program Files\Java\jdk-21.0.10\bin\java.exe'){throw '正式账户/Java路径不匹配'}
$actual=Get-Inc02ProcessIdentity -ProcessId $saved.pid -Port 18080
Assert-Inc02ProcessIdentity $saved $actual
if(-not $Execute){@{identity=$actual;executed=$false;action='只读完整身份检查'}|ConvertTo-Json -Depth 6;return}
$gate=Get-Content "$root/docs/evidence/s69-inc02/recovery-gate.json" -Raw|ConvertFrom-Json
if(-not $UserApproved -or -not $gate.formalStopApproved){throw '没有正式受控停止批准；未调用shutdown'}
. "$PSScriptRoot/load-local-env.ps1"
if(-not $env:HUANKAN_ADMIN_TOKEN){throw '管理令牌缺失'}
$again=Get-Inc02ProcessIdentity -ProcessId $saved.pid -Port 18080
Assert-Inc02ProcessIdentity $saved $again
Invoke-RestMethod -Method Post -Uri 'http://127.0.0.1:18080/actuator/shutdown' -Headers @{Authorization="Bearer $env:HUANKAN_ADMIN_TOKEN"} -ContentType 'application/json' -Body '{}' -TimeoutSec 5|Out-Null
$p=Get-Process -Id $saved.pid -ErrorAction SilentlyContinue
if($p -and -not $p.WaitForExit(15000)){throw '正式正常关闭超时，保留现场；禁止强杀'}
Write-Output '正式原版已正常退出；未操作PG、前端或任何其他进程。'
