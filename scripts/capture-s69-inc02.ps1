$ErrorActionPreference='Stop'
$root=(Resolve-Path (Split-Path -Parent $PSScriptRoot)).Path
$dest=Join-Path $root 'docs/evidence/s69-inc02'
if(Test-Path $dest){throw 'INC02证据目录已存在，拒绝覆盖；应选新调查目录'}
New-Item -ItemType Directory -Path $dest|Out-Null
$ports=@(Get-NetTCPConnection -State Listen|Where-Object LocalPort -in @(5432,55432,55434,18080,18087,18088,5173,5180,5181,5182))
$all=@(Get-CimInstance Win32_Process)
$ids=@($ports.OwningProcess)+@(43884,42500,43660,49556)
@{at=[datetimeoffset]::Now.ToString('o');timezone=(Get-TimeZone).Id;account=[Security.Principal.WindowsIdentity]::GetCurrent().Name;machine=$env:COMPUTERNAME;boot=(Get-CimInstance Win32_OperatingSystem).LastBootUpTime;command='Get-NetTCPConnection；Win32_Process；Win32_Service；Get-ScheduledTask（只读）';ports=@($ports|Select-Object LocalAddress,LocalPort,OwningProcess);processes=@($all|Where-Object ProcessId -in $ids|Select-Object ProcessId,ParentProcessId,CreationDate,ExecutablePath,CommandLine);services=@(Get-CimInstance Win32_Service|Where-Object Name -Match 'huankan|postgres'|Select-Object Name,State,ProcessId,StartName,PathName);scheduledTasks=@(Get-ScheduledTask|Where-Object {($_.Actions|ConvertTo-Json -Depth 8 -Compress) -match 'HUANKAN'}|Select-Object TaskName,TaskPath,State,Actions);git=@(& git -C $root status --porcelain=v1)}|ConvertTo-Json -Depth 12|Set-Content "$dest/baseline.json" -Encoding utf8
$files=@(Get-Item "$root/backend/target/huankan-s70-reviewed-0.1.0.jar","$root/backend/target/huankan-s69-r3-candidate-0.1.0.jar")
$files+=@(Get-ChildItem "$root/scripts" -File|Where-Object Extension -in @('.ps1','.mjs','.py'))
$files+=@(Get-ChildItem "$root/docs" -File|Where-Object Name -Match 'S69-INC01|S69-R4|统一开发状态')
$files+=@(Get-ChildItem "$root/runtime" -File|Where-Object Name -Match '^s70-main|^s70-web|^s69-r4-owned.*process|^s69-r4-test-process')
$manifest=@()
foreach($f in $files){$rel=[IO.Path]::GetRelativePath($root,$f.FullName);$copy=Join-Path $dest "before/$rel";New-Item -ItemType Directory -Force -Path (Split-Path $copy)|Out-Null;[IO.File]::Copy($f.FullName,$copy,$false);$manifest+=@{source=$rel;snapshot=[IO.Path]::GetRelativePath($root,$copy);sha256=(Get-FileHash $copy).Hash.ToLowerInvariant();bytes=(Get-Item $copy).Length}}
$manifest|ConvertTo-Json -Depth 5|Set-Content "$dest/baseline-manifest.json" -Encoding utf8
Copy-Item -LiteralPath "$root/docs/evidence/s69-inc01/20261014T063813Z/agent-history-v2.json" -Destination "$dest/prior-policy-rejection.json"
@{at=[datetimeoffset]::Now.ToString('o');files=$manifest.Count;dest=$dest;formalProcessesStarted=$false}|ConvertTo-Json
