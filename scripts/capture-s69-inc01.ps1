$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$base=Join-Path $root 'docs/evidence/s69-inc01'
$stamp=(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$dest=Join-Path $base $stamp
New-Item -ItemType Directory -Path $dest -ErrorAction Stop | Out-Null
$commands=[Collections.Generic.List[object]]::new()
function Capture([string]$name,[string]$description,[scriptblock]$action){
 $start=[datetimeoffset]::Now; $errorText=$null
 try{$data=& $action}catch{$errorText=$_.Exception.Message;$data=$null}
 @{startedAt=$start.ToString('o');finishedAt=[datetimeoffset]::Now.ToString('o');command=$description;result=$data;error=$errorText}|ConvertTo-Json -Depth 14|Set-Content -LiteralPath (Join-Path $dest "$name.json") -Encoding utf8
 $commands.Add(@{name=$name;command=$description;error=$errorText})
}
Capture 'machine' 'Get-Date; Get-TimeZone; Win32_OperatingSystem; identity' {
 $os=Get-CimInstance Win32_OperatingSystem
 @{time=[datetimeoffset]::Now.ToString('o');utc=[datetime]::UtcNow.ToString('o');timezone=(Get-TimeZone).Id;machine=$env:COMPUTERNAME;account=[Security.Principal.WindowsIdentity]::GetCurrent().Name;os=$os.Caption;lastBoot=$os.LastBootUpTime.ToUniversalTime().ToString('o');freeMemoryKB=$os.FreePhysicalMemory;totalMemoryKB=$os.TotalVisibleMemorySize}
}
Capture 'git' 'git status --porcelain=v1; git branch --show-current; git rev-parse --verify HEAD' { @{status=@(& git -C $root status --porcelain=v1);branch=(& git -C $root branch --show-current);head=(& git -C $root rev-parse --verify HEAD 2>&1|Out-String)} }
Capture 'processes' 'Get-CimInstance Win32_Process; Get-NetTCPConnection -State Listen (target ports); relevant parent identities' {
 $all=@(Get-CimInstance Win32_Process);$ports=@(Get-NetTCPConnection -State Listen|Where-Object LocalPort -in @(5432,55432,55434,18080,18083,18084,18085,18086,18087,18088,5173,5176,5180,5181,5182,5183,5184))
 $selected=@($all|Where-Object { $_.CommandLine -match 'HUANKAN|bizpulse|postgres' -or $_.ProcessId -in @($ports.OwningProcess)+@(43884,42500,43660,49556) })
 $parentIds=@($selected.ParentProcessId)
 @{ports=@($ports|Select-Object LocalAddress,LocalPort,OwningProcess);processes=@($all|Where-Object { $_ -in $selected -or $_.ProcessId -in $parentIds }|ForEach-Object { @{pid=$_.ProcessId;parentPid=$_.ParentProcessId;createdAt=$_.CreationDate.ToUniversalTime().ToString('o');executable=$_.ExecutablePath;command=($_.CommandLine -replace '(?i)((?:password|token|api[-_]?key)[=\s]+)[^\s]+','$1[REDACTED]')} })}
}
$manifest=[Collections.Generic.List[object]]::new()
$sources=@(Get-ChildItem (Join-Path $root 'runtime') -File|Where-Object { $_.Extension -in @('.log','.json','.txt') -and $_.Name -match '^(s69|s70|s6[456]|postgres|backend|frontend|isolated)' })
$sources+=@(Get-ChildItem (Join-Path $root 'scripts') -File|Where-Object Extension -in @('.ps1','.mjs'))
$sources+=@(Get-ChildItem (Join-Path $root 'docs/evidence') -File|Where-Object Name -match '^s69.*(process|summary|capacity|final-preservation|delivery|final-index|browser|cleanup|rollback|formal-evidence)')
$sources+=@(Get-ChildItem (Join-Path $root 'docs') -File|Where-Object { $_.Name -match '^S69|统一开发状态报告' })
$sources+=@(Get-Item (Join-Path $root 'backend/src/main/resources/application.yml'),(Join-Path $root 'backend/target/huankan-s70-reviewed-0.1.0.jar'),(Join-Path $root 'backend/target/huankan-s69-r3-candidate-0.1.0.jar'))
foreach($f in $sources){
 $relative=[IO.Path]::GetRelativePath($root,$f.FullName);$copy=Join-Path $dest ('frozen/'+$relative)
 New-Item -ItemType Directory -Force -Path (Split-Path $copy) | Out-Null
 $before=(Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
 [IO.File]::Copy($f.FullName,$copy,$false)
 $copied=(Get-FileHash -LiteralPath $copy -Algorithm SHA256).Hash.ToLowerInvariant()
 $after=(Get-FileHash -LiteralPath $f.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
 $manifest.Add(@{source=$relative;snapshot=[IO.Path]::GetRelativePath($root,$copy);bytes=(Get-Item $copy).Length;sha256=$copied;sourceBefore=$before;sourceAfter=$after;stable=($before -eq $copied -and $copied -eq $after);sourceLastWriteUtc=$f.LastWriteTimeUtc.ToString('o')})
}
$manifest|ConvertTo-Json -Depth 5|Set-Content (Join-Path $dest 'freeze-manifest.json') -Encoding utf8
@{startedAt=$stamp;completedAt=[datetimeoffset]::Now.ToString('o');root=$root;commands=$commands;frozenFiles=$manifest.Count;unstable=@($manifest|Where-Object stable -eq $false)}|ConvertTo-Json -Depth 5|Set-Content (Join-Path $dest 'capture-summary.json') -Encoding utf8
Write-Output $dest
