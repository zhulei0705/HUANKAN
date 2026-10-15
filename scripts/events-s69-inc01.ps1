$ErrorActionPreference='Stop'
$dest=Join-Path (Split-Path -Parent $PSScriptRoot) 'docs/evidence/s69-inc01/20261014T063813Z'
$start=[datetimeoffset]::Parse('2026-10-14T12:45:00+08:00').LocalDateTime
$end=[datetimeoffset]::Parse('2026-10-14T14:05:00+08:00').LocalDateTime
$logs=@('Application','System','Security','Microsoft-Windows-PowerShell/Operational','Windows PowerShell','Microsoft-Windows-TaskScheduler/Operational','Microsoft-Windows-Windows Defender/Operational','Microsoft-Windows-TerminalServices-LocalSessionManager/Operational','Microsoft-Windows-Sysmon/Operational','Microsoft-Windows-Resource-Exhaustion-Detector/Operational')
$summary=@()
foreach($log in $logs){
 $at=[datetimeoffset]::Now.ToString('o');$err=$null;$items=@()
 try{$items=@(Get-WinEvent -FilterHashtable @{LogName=$log;StartTime=$start;EndTime=$end} -ErrorAction Stop|ForEach-Object { @{time=$_.TimeCreated.ToUniversalTime().ToString('o');id=$_.Id;provider=$_.ProviderName;recordId=$_.RecordId;level=$_.LevelDisplayName;message=$_.Message;xml=$_.ToXml()} })}catch{$err=$_.Exception.Message}
 $name=($log -replace '[^a-zA-Z0-9-]','_')+'.json'
 @{at=$at;command="Get-WinEvent -FilterHashtable LogName=$log StartTime=2026-10-14T12:45:00+08:00 EndTime=2026-10-14T14:05:00+08:00";error=$err;events=$items}|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $dest $name) -Encoding utf8
 $summary+=@{log=$log;count=$items.Count;error=$err;file=$name}
}
$paths=@("$env:LOCALAPPDATA/CrashDumps","$env:ProgramData/Microsoft/Windows/WER/ReportArchive","$env:ProgramData/Microsoft/Windows/WER/ReportQueue","$env:LOCALAPPDATA/Microsoft/Windows/WER")
$wer=@();foreach($path in $paths){try{$wer+=@{path=$path;files=@(Get-ChildItem -LiteralPath $path -Recurse -File -ErrorAction Stop|Select-Object FullName,Length,LastWriteTimeUtc)}}catch{$wer+=@{path=$path;error=$_.Exception.Message}}}
@{at=[datetimeoffset]::Now.ToString('o');paths=$wer}|ConvertTo-Json -Depth 6|Set-Content (Join-Path $dest 'wer-inventory.json') -Encoding utf8
try{$tasks=@(Get-ScheduledTask -ErrorAction Stop|Where-Object { ($_.Actions|ConvertTo-Json -Compress) -match 'HUANKAN|java|node|powershell|postgres|codex' }|ForEach-Object { $info=Get-ScheduledTaskInfo $_;@{name=$_.TaskName;path=$_.TaskPath;actions=$_.Actions;lastRun=$info.LastRunTime;lastResult=$info.LastTaskResult;state=$_.State} });@{tasks=$tasks;error=$null}|ConvertTo-Json -Depth 8|Set-Content (Join-Path $dest 'scheduled-tasks.json') -Encoding utf8}catch{@{error=$_.Exception.Message}|ConvertTo-Json|Set-Content (Join-Path $dest 'scheduled-tasks.json') -Encoding utf8}
$summary|ConvertTo-Json -Depth 5|Set-Content (Join-Path $dest 'events-summary.json') -Encoding utf8
$summary|ConvertTo-Json -Depth 5
