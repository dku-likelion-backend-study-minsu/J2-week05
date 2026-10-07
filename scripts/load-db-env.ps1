$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.local/mysql.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Missing .local/mysql.env. Run scripts/setup-local-mysql.ps1 first.'
}
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
    if ($line -match '^([^#=]+)=(.*)$') { $settings[$matches[1]] = $matches[2] }
}
$env:DB_USERNAME = $settings['MYSQL_USER']
$env:DB_PASSWORD = $settings['MYSQL_PASSWORD']
$env:DB_URL = 'jdbc:mysql://127.0.0.1:13308/j2_week05?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
$env:DB_TEST_URL = 'jdbc:mysql://127.0.0.1:13308/j2_week05_test?serverTimezone=Asia/Seoul&characterEncoding=UTF-8'
