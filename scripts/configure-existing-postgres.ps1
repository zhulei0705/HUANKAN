param([string]$PgBin='D:\Program Files\PostgreSQL\18\bin', [int]$Port=5432)
$ErrorActionPreference='Stop'
if (-not $env:PGPASSWORD) { throw '请在当前进程设置PGPASSWORD；脚本不保存管理员密码' }
. "$PSScriptRoot\load-local-env.ps1"
$exists=& "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -w -At -c "SELECT count(*) FROM pg_database WHERE datname='huankan'"
if($LASTEXITCODE -ne 0){throw '现有PostgreSQL连接失败'}
if($exists -ne '0'){throw '已有同名huankan库；未修改，需先检查归属'}
$role=& "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -w -At -c "SELECT count(*) FROM pg_roles WHERE rolname='huankan_app'"
if($role -ne '0'){throw '已有同名账号；未修改，需先检查归属'}
if(-not $env:HUANKAN_DB_PASSWORD){throw '请先生成项目应用账号密码'}
$quotedPassword=$env:HUANKAN_DB_PASSWORD.Replace("'","''")
"CREATE ROLE huankan_app LOGIN PASSWORD '$quotedPassword';" | & "$PgBin\psql.exe" -h 127.0.0.1 -p $Port -U postgres -d postgres -w -v ON_ERROR_STOP=1
if($LASTEXITCODE -ne 0){throw '应用账号创建失败'}
& "$PgBin\createdb.exe" -h 127.0.0.1 -p $Port -U postgres -O huankan_app huankan
if($LASTEXITCODE -ne 0){throw '独立业务库创建失败'}
[Environment]::SetEnvironmentVariable('HUANKAN_DB_URL',"jdbc:postgresql://127.0.0.1:$Port/huankan",'User')
[Environment]::SetEnvironmentVariable('HUANKAN_DB_USER','huankan_app','User')
Write-Output "已复用现有PostgreSQL，独立业务库huankan，应用账号huankan_app（非超级用户），未操作其他数据库。"
