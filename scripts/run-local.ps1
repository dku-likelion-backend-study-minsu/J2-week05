$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'load-db-env.ps1')
$env:SPRING_DATASOURCE_URL = $env:DB_URL
$env:SPRING_PROFILES_ACTIVE = 'dev'
Push-Location $projectRoot
try {
    & .\gradlew.bat bootRun --console=plain
    $runResult = $LASTEXITCODE
} finally { Pop-Location }
if ($runResult -ne 0) { throw "Gradle bootRun failed with exit code $runResult" }
