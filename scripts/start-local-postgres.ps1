param([string]$PgBin = 'D:\Program Files\PostgreSQL\18\bin', [int]$Port = 55432)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$data = Join-Path $root 'runtime\postgres-data'
if (-not (Test-Path "$PgBin\initdb.exe")) { throw 'PostgreSQL程序不存在，请设置-PgBin' }
. "$PSScriptRoot\load-local-env.ps1"
function New-Secret {
    $bytes = New-Object byte[] 36
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes); $rng.Dispose()
    [Convert]::ToBase64String($bytes)
}
if (-not (Test-Path "$data\PG_VERSION")) {
    if ((Test-Path $data) -and (Get-ChildItem -Force $data)) { throw '数据目录非空，不执行初始化' }
    New-Item -ItemType Directory -Force (Join-Path $root 'runtime') | Out-Null
    $admin = New-Secret
    [Environment]::SetEnvironmentVariable('HUANKAN_LOCAL_ADMIN_PASSWORD', $admin, 'User')
    $passwordFile = Join-Path $root 'runtime\init-password.tmp'
    [IO.File]::WriteAllText($passwordFile, $admin, [Text.UTF8Encoding]::new($false))
    try {
        & "$PgBin\initdb.exe" -D $data -U huankan_admin --auth=scram-sha-256 --encoding=UTF8 --locale=C --pwfile=$passwordFile
        if ($LASTEXITCODE -ne 0) { throw 'initdb失败；未更改已有PostgreSQL服务' }
    } finally { Remove-Item -LiteralPath $passwordFile -ErrorAction SilentlyContinue }
    Add-Content -LiteralPath "$data\postgresql.conf" -Value "`nlisten_addresses = '127.0.0.1'`nport = $Port"
    . "$PSScriptRoot\load-local-env.ps1"
}
& "$PgBin\pg_ctl.exe" -D $data status *> $null
if ($LASTEXITCODE -ne 0) {
    Start-Process -FilePath "$PgBin\pg_ctl.exe" -ArgumentList @('-D',$data,'-l',"$root\runtime\postgres.log",'start') -WindowStyle Hidden | Out-Null
    $ready=$false
    for($attempt=0;$attempt -lt 30;$attempt++) { & "$PgBin\pg_isready.exe" -h 127.0.0.1 -p $Port *> $null; if($LASTEXITCODE -eq 0){$ready=$true;break};Start-Sleep -Milliseconds 500 }
    if (-not $ready) { throw '项目独立PostgreSQL启动失败，查看runtime/postgres.log' }
}
$env:PGPASSWORD = $env:HUANKAN_LOCAL_ADMIN_PASSWORD
try {
    foreach ($kind in @('app','test')) {
        $role = "huankan_$kind"; $database = if ($kind -eq 'app') { 'huankan' } else { 'huankan_test' }
        $exists = & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U huankan_admin -d postgres -At -c "SELECT 1 FROM pg_roles WHERE rolname='$role'"
        if ($LASTEXITCODE -ne 0) { throw '本项目管理员连接失败' }
        if ($exists -ne '1') {
            $secret = New-Secret
            "CREATE ROLE $role LOGIN PASSWORD '$secret';" | & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U huankan_admin -d postgres -v ON_ERROR_STOP=1
            if ($LASTEXITCODE -ne 0) { throw '角色创建失败' }
            $prefix = if ($kind -eq 'app') { 'HUANKAN_DB' } else { 'HUANKAN_TEST_DB' }
            [Environment]::SetEnvironmentVariable("${prefix}_PASSWORD", $secret, 'User')
            [Environment]::SetEnvironmentVariable("${prefix}_USER", $role, 'User')
            [Environment]::SetEnvironmentVariable("${prefix}_URL", "jdbc:postgresql://127.0.0.1:$Port/$database", 'User')
        }
        $exists = & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U huankan_admin -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname='$database'"
        if ($exists -ne '1') {
            & "$PgBin\createdb.exe" -h 127.0.0.1 -p $Port -U huankan_admin -O $role $database
            if ($LASTEXITCODE -ne 0) { throw '数据库创建失败' }
        }
    }
    if (-not $env:HUANKAN_ADMIN_TOKEN) { [Environment]::SetEnvironmentVariable('HUANKAN_ADMIN_TOKEN', (New-Secret), 'User') }
} finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
. "$PSScriptRoot\load-local-env.ps1"
Write-Output "本项目PostgreSQL已就绪：127.0.0.1:$Port；业务库huankan；隔离测试库huankan_test。凭证未打印。"
