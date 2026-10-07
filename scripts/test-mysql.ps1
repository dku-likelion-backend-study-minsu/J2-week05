$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'load-db-env.ps1')
$savedUrl = $env:SPRING_DATASOURCE_URL
$savedProfiles = $env:SPRING_PROFILES_ACTIVE
$env:SPRING_DATASOURCE_URL = $env:DB_TEST_URL
$env:SPRING_PROFILES_ACTIVE = 'test'
Push-Location $projectRoot
try {
    & .\gradlew.bat test --no-daemon --console=plain @args
    $testResult = $LASTEXITCODE
} finally {
    Pop-Location
    $env:SPRING_DATASOURCE_URL = $savedUrl
    $env:SPRING_PROFILES_ACTIVE = $savedProfiles
}
if ($testResult -ne 0) { throw "Gradle test failed with exit code $testResult" }
