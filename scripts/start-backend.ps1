param(
    [ValidateSet('local', 'demo')]
    [string]$Profile = 'local'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'No se encontró .env. Copia .env.example a .env y configura credenciales locales.'
}

Get-Content -LiteralPath $envFile | ForEach-Object {
    if ($_ -match '^\s*([^#=\s]+)\s*=\s*(.*)\s*$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}

[Environment]::SetEnvironmentVariable('SPRING_PROFILES_ACTIVE', $Profile, 'Process')

Push-Location $root
try {
    mvn -f app/pom.xml spring-boot:run
} finally {
    Pop-Location
}

