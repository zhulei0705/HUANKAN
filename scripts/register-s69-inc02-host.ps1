# 默认仅展示待批准任务。不得把参数视为真实用户授权。
param([switch]$ExecuteRegistration,[switch]$UserApproved,[pscredential]$Credential)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$path="$PSScriptRoot/inc02/HUANKAN-S69-Original.task.xml"
[xml]$xml=Get-Content $path -Raw
if($xml.Task.Settings.Enabled -ne 'false' -or $xml.Task.Triggers.ChildNodes.Count -ne 0){throw '任务模板必须禁用且无触发器'}
if(-not $ExecuteRegistration){@{task='HUANKAN-S69-Original';account='DESKTOP-JMS8B0H\sthl';enabled=$false;triggers=0;autoRestart=$false;action='仅准备，不注册';xml=$path}|ConvertTo-Json;return}
$gate=Get-Content "$root/docs/evidence/s69-inc02/recovery-gate.json" -Raw|ConvertFrom-Json
if(-not $UserApproved -or -not $gate.hostRegistrationApproved){throw '没有独立托管配置批准；未注册任何任务'}
if(-not $Credential -or $Credential.UserName -ne 'DESKTOP-JMS8B0H\sthl'){throw '必须由用户通过安全凭据界面提供计划账户，不能使用管理员或系统账户替代'}
if(Get-ScheduledTask -TaskName 'HUANKAN-S69-Original' -ErrorAction SilentlyContinue){throw '任务已存在，拒绝覆盖'}
# 只注册禁用、无触发器任务；不启用、不运行、不设置开机启动。口令不写文件或日志。
Register-ScheduledTask -TaskName 'HUANKAN-S69-Original' -Xml (Get-Content $path -Raw) -User $Credential.UserName -Password $Credential.GetNetworkCredential().Password|Out-Null
Write-Output '已注册禁用任务；未启用或启动。正式启动仍须独立技术Go及用户批准。'
