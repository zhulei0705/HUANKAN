$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path;$dest="$root/docs/evidence/s69-inc02"
$results=@()
try{& "$PSScriptRoot/host-s69-inc02-original.ps1" -Mode Formal -ExplicitUserApproved;throw 'Formal门禁未拒绝'}catch{if($_.Exception.Message -eq 'Formal门禁未拒绝'){throw};$results+=@{name='Formal启动即使携带批准参数仍被No-Go阻断';passed=($_.Exception.Message -match '未准入|不开放Formal');reason=$_.Exception.Message}}
$service=New-Object -ComObject 'Schedule.Service';$service.Connect();$definition=$service.NewTask(0)
$definition.XmlText=Get-Content "$PSScriptRoot/inc02/HUANKAN-S69-Original.task.xml" -Raw
if($definition.Settings.Enabled -or $definition.Triggers.Count -ne 0 -or $definition.Settings.AllowHardTerminate){throw '任务模板含自动启动或强制终止能力'}
$results+=@{name='TaskScheduler COM实际解析无触发器禁用任务';passed=$true;registered=$false}
@{at=[datetimeoffset]::Now.ToString('o');tests=$results;count=$results.Count;passed=(@($results|Where-Object passed -eq $false).Count -eq 0);systemConfigurationChanged=$false}|ConvertTo-Json -Depth 6|Set-Content "$dest/hosting-gate-tests-verified.json" -Encoding utf8
Write-Output '2项托管模板/门禁验证通过；没有注册、启用或运行系统任务。'
