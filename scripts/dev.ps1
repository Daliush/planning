param(
    [ValidateSet('start', 'stop', 'status', 'health')][string]$Action = 'start',
    [string]$ProjectName,
    [string]$EnvFile,
    [ValidateRange(0, 65535)][int]$DatabasePort = 0,
    [ValidateRange(0, 65535)][int]$ApiPort = 0,
    [ValidateRange(0, 65535)][int]$FrontendPort = 0,
    [ValidateRange(1, 1800)][int]$TimeoutSeconds = 180,
    [switch]$SkipBuild
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime.ps1')

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
    $configuration = Get-PlanningConfiguration
    switch ($Action) {
        'start' {
            $arguments = @('up', '-d', '--wait', '--wait-timeout', [string]$TimeoutSeconds)
            if (!$SkipBuild) { $arguments += '--build' }
            Invoke-PlanningCompose -Arguments ($arguments + $PlanningServices)
            Wait-PlanningHealth -Configuration $configuration -TimeoutSeconds $TimeoutSeconds
        }
        'stop' {
            # Arrêt des trois services de ce projet seulement ; le volume reste intact.
            Invoke-PlanningCompose -Arguments (@('stop') + $PlanningServices)
        }
        'status' { Invoke-PlanningCompose -Arguments @('ps', '--all') }
        'health' { Wait-PlanningHealth -Configuration $configuration -TimeoutSeconds $TimeoutSeconds }
    }
} finally {
    Restore-PlanningEnvironment -PreviousValues $previousValues
}
