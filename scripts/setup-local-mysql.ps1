$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$containerName = 'j2-week05-mysql-20261007'
$volumeName = 'j2-week05-mysql-data-20261007'
$envFile = Join-Path $projectRoot '.local/mysql.env'
$containers = @(docker ps -a --format '{{.Names}}')
if ($LASTEXITCODE -ne 0) { throw 'Docker is unavailable; no existing container was changed.' }
$volumes = @(docker volume ls --format '{{.Name}}')
if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect Docker volumes.' }
if ($containers -contains $containerName -or $volumes -contains $volumeName) {
    throw 'Dedicated container or volume already exists. Inspect it before reuse; this script does not replace it.'
}
if (Get-NetTCPConnection -State Listen -LocalPort 13308 -ErrorAction SilentlyContinue) {
    throw 'Port 13308 is occupied. No container was started.'
}
if (Test-Path -LiteralPath $envFile) {
    throw '.local/mysql.env already exists. It was preserved; inspect it and use docker compose up -d mysql if appropriate.'
}
function New-RandomPassword {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
New-Item -ItemType Directory -Path (Split-Path -Parent $envFile) -Force | Out-Null
$content = "MYSQL_DATABASE=j2_week05`nMYSQL_USER=j2_week05`nMYSQL_PASSWORD=$(New-RandomPassword)`nMYSQL_ROOT_PASSWORD=$(New-RandomPassword)`n"
[System.IO.File]::WriteAllText($envFile, $content, (New-Object System.Text.UTF8Encoding($false)))
Push-Location $projectRoot
try {
    docker compose -p j2-week05 up -d mysql
    if ($LASTEXITCODE -ne 0) { throw 'Dedicated MySQL startup failed. Existing project containers were not modified.' }
} finally { Pop-Location }
Write-Output 'Dedicated MySQL configured. Load scripts/load-db-env.ps1 before running Gradle.'
