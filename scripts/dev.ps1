param(
    [ValidateSet('start', 'stop', 'status', 'health')][string]$Action = 'start',
    [ValidateSet('built', 'dev')][string]$Mode = 'built',
    [string]$ProjectName,
    [string]$EnvFile,
    [ValidateRange(0, 65535)][int]$DatabasePort = 0,
    [ValidateRange(0, 65535)][int]$ApiPort = 0,
    [ValidateRange(0, 65535)][int]$FrontendPort = 0,
    [ValidateRange(1, 1800)][int]$TimeoutSeconds = 180,
    [switch]$SkipBuild,
    [switch]$SeedDemo,
    [string]$DemoWeekStart
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime.ps1')

if ($SeedDemo -and $Action -ne 'start') { throw '-SeedDemo nécessite -Action start.' }
if ($DemoWeekStart -and !$SeedDemo) { throw '-DemoWeekStart nécessite -SeedDemo.' }
$seedScript = Join-Path $PSScriptRoot 'seed-demo.ps1'
if ($SeedDemo -and !(Test-Path -LiteralPath $seedScript -PathType Leaf)) { throw "Script de démonstration absent : $seedScript" }

# Une configuration de développement locale est créée une seule fois.
if ($Action -eq 'start' -and !$EnvFile) {
    $localEnvironment = Join-Path $PlanningRoot '.env'
    if (!(Test-Path -LiteralPath $localEnvironment)) {
        $randomBytes = New-Object byte[] 24
        $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $generator.GetBytes($randomBytes) } finally { $generator.Dispose() }
        $localPassword = [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
        $example = Get-Content -LiteralPath (Join-Path $PlanningRoot '.env.example') -Raw -Encoding UTF8
        $example = $example.Replace('PLANNING_DB_PASSWORD=change-me-local-only', "PLANNING_DB_PASSWORD=$localPassword")
        [IO.File]::WriteAllText($localEnvironment, $example, [Text.UTF8Encoding]::new($false))
        Write-Host 'Configuration locale créée dans .env.'
    }
}

$previousValues = Set-PlanningPortOverrides -DatabasePort $DatabasePort -ApiPort $ApiPort -FrontendPort $FrontendPort
try {
    $configuration = Get-PlanningConfiguration -Mode $Mode
    switch ($Action) {
        'start' {
            $arguments = @('up', '-d', '--wait', '--wait-timeout', [string]$TimeoutSeconds)
            if (!$SkipBuild) { $arguments += '--build' }
            Invoke-PlanningCompose -Arguments ($arguments + $PlanningServices) -Mode $Mode
            Wait-PlanningHealth -Configuration $configuration -TimeoutSeconds $TimeoutSeconds -Mode $Mode
            if ($SeedDemo) {
                $seedParameters = @{
                    ProjectName = $configuration.name
                    EnvFile = $EnvFile
                    DatabasePort = [int](($configuration.services.'planning-db'.ports | Where-Object target -eq 5432 | Select-Object -First 1).published)
                    ApiPort = [int](($configuration.services.'planning-api'.ports | Where-Object target -eq 8081 | Select-Object -First 1).published)
                    FrontendPort = [int](($configuration.services.'planning-web'.ports | Where-Object target -eq 80 | Select-Object -First 1).published)
                }
                if ($DemoWeekStart) { $seedParameters.WeekStart = $DemoWeekStart }
                & $seedScript @seedParameters
            }
        }
        'stop' {
            # Arrêt des trois services de ce projet seulement ; le volume reste intact.
            Invoke-PlanningCompose -Arguments (@('stop') + $PlanningServices) -Mode $Mode
        }
        'status' { Invoke-PlanningCompose -Arguments @('ps', '--all') -Mode $Mode }
        'health' { Wait-PlanningHealth -Configuration $configuration -TimeoutSeconds $TimeoutSeconds -Mode $Mode }
    }
} finally {
    Restore-PlanningEnvironment -PreviousValues $previousValues
}
