$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$pwsh=(Get-Process -Id $PID).Path
$args=@('-NoProfile','-File',"$PSScriptRoot/host-s69-inc02-original.ps1",'-Mode','Isolated','-IsolationFile',"$root/docs/evidence/s69-inc02/isolation-r3.json",'-RunName','original-run')
$p=Start-Process -FilePath $pwsh -ArgumentList $args -WorkingDirectory $root -WindowStyle Hidden -PassThru -RedirectStandardOutput "$root/docs/evidence/s69-inc02/supervisor-stdout.log" -RedirectStandardError "$root/docs/evidence/s69-inc02/supervisor-stderr.log"
@{at=[datetimeoffset]::Now.ToString('o');launcherPid=$PID;supervisorPid=$p.Id;purpose='验证直接启动器退出后监督器和Java存活；不能替代Codex宿主更新独立性验收'}|ConvertTo-Json|Set-Content "$root/docs/evidence/s69-inc02/launcher.json" -Encoding utf8
# 立即退出直接启动器，本轮不停止Codex、用户会话或任何系统服务。
