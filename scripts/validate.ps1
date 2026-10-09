param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$Runtime,
    [switch]$Functional,
    [string]$ProjectName = 'planning-validation',
    [string]$EnvFile,
    [ValidateRange(1, 65535)][int]$DatabasePort = 15435,
    [ValidateRange(1, 65535)][int]$ApiPort = 18081,
    [ValidateRange(1, 65535)][int]$FrontendPort = 15174,
    [ValidateRange(1, 1800)][int]$TimeoutSeconds = 180
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime.ps1')
if ($Functional -and !$Runtime) { throw '-Functional nécessite -Runtime pour utiliser une base de test isolée.' }

& (Join-Path $PSScriptRoot 'check-autonomy.ps1')

# Le fichier exemple suffit aux contrôles de configuration et à la stack de test.
if (!$EnvFile -and !(Test-Path -LiteralPath (Join-Path $PlanningRoot '.env'))) { $EnvFile = '.env.example' }
$previousValues = Set-PlanningPortOverrides -DatabasePort $DatabasePort -ApiPort $ApiPort -FrontendPort $FrontendPort
$started = $false
try {
    $configuration = Get-PlanningConfiguration
    Write-Host 'Configuration Docker Compose valide.'
    if (!$SkipBackend) {
        Write-Host 'Tests backend (Java 21 dans Docker).'
        $backendPath = Join-Path $PlanningRoot 'backend'
        & docker run --rm --mount "type=bind,source=$backendPath,target=/app" -w /app maven:3.9-eclipse-temurin-21 mvn -B --no-transfer-progress test
        if ($LASTEXITCODE -ne 0) { throw 'Les tests backend ont échoué.' }
    }
    if (!$SkipFrontend) {
        Write-Host 'Installation reproductible et build frontend.'
        Push-Location (Join-Path $PlanningRoot 'frontend')
        try {
            & npm ci
            if ($LASTEXITCODE -ne 0) { throw 'npm ci a échoué.' }
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw 'Le build frontend a échoué.' }
        } finally { Pop-Location }
    }
    if ($Runtime) {
        $running = @(Invoke-PlanningCompose -Arguments @('ps', '--status', 'running', '--quiet'))
        if ($running.Count -gt 0) { throw "Le projet $ProjectName tourne déjà. Choisir un autre -ProjectName pour ne pas l'arrêter." }
        $started = $true
        & (Join-Path $PSScriptRoot 'dev.ps1') -Action start -ProjectName $ProjectName -EnvFile $EnvFile -DatabasePort $DatabasePort -ApiPort $ApiPort -FrontendPort $FrontendPort -TimeoutSeconds $TimeoutSeconds
        if ($Functional) {
            $urls = Get-PlanningUrls -Configuration $configuration
            & (Join-Path $PSScriptRoot 'validate-workforce.ps1') -BaseUrl $urls.Api
            & (Join-Path $PSScriptRoot 'validate-contracts.ps1') -BaseUrl $urls.Api
        }
    }
    Write-Host 'Validation Planning réussie.'
} finally {
    try {
        if ($started) {
            Invoke-PlanningCompose -Arguments (@('stop') + $PlanningServices)
            Write-Host 'Services de validation arrêtés ; leur volume de données est conservé.'
        }
    } finally { Restore-PlanningEnvironment -PreviousValues $previousValues }
}
