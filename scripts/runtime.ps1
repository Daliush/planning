# Fonctions communes aux scripts ; tous les chemins restent dans Planning.
$PlanningRoot = Split-Path -Parent $PSScriptRoot
$PlanningServices = @('planning-db', 'planning-api', 'planning-web')

function Invoke-PlanningCompose {
    param([string[]]$Arguments)
    $composeArguments = @('compose', '--project-directory', $PlanningRoot, '--file', (Join-Path $PlanningRoot 'docker-compose.yml'))
    if ($ProjectName) { $composeArguments += @('--project-name', $ProjectName) }
    $configurationFile = $EnvFile
    if (!$configurationFile -and (Test-Path -LiteralPath (Join-Path $PlanningRoot '.env'))) {
        $configurationFile = Join-Path $PlanningRoot '.env'
    }
    if ($configurationFile) {
        if (![IO.Path]::IsPathRooted($configurationFile)) { $configurationFile = Join-Path $PlanningRoot $configurationFile }
        if (!(Test-Path -LiteralPath $configurationFile -PathType Leaf)) { throw "Fichier d'environnement absent : $configurationFile" }
        $composeArguments += @('--env-file', $configurationFile)
    }
    & docker @composeArguments @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose a échoué (code $LASTEXITCODE)." }
}

function Get-PlanningConfiguration {
    $configurationJson = Invoke-PlanningCompose -Arguments @('config', '--format', 'json')
    return ($configurationJson | ConvertFrom-Json)
}

function Get-PlanningUrls {
    param($Configuration)
    $apiPort = ($Configuration.services.'planning-api'.ports | Where-Object target -eq 8081 | Select-Object -First 1).published
    $frontendPort = ($Configuration.services.'planning-web'.ports | Where-Object target -eq 80 | Select-Object -First 1).published
    return @{ Api = "http://127.0.0.1:$apiPort"; Frontend = "http://127.0.0.1:$frontendPort" }
}

function Set-PlanningPortOverrides {
    param([int]$DatabasePort, [int]$ApiPort, [int]$FrontendPort)
    $previousValues = @{}
    $overrides = @{ PLANNING_DB_PORT = $DatabasePort; PLANNING_PORT = $ApiPort; PLANNING_FRONTEND_PORT = $FrontendPort }
    foreach ($entry in $overrides.GetEnumerator()) {
        if ($entry.Value -gt 0) {
            $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, 'Process')
            [Environment]::SetEnvironmentVariable($entry.Key, [string]$entry.Value, 'Process')
        }
    }
    return $previousValues
}

function Restore-PlanningEnvironment {
    param([hashtable]$PreviousValues)
    foreach ($entry in $PreviousValues.GetEnumerator()) {
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, 'Process')
    }
}

function Wait-PlanningHealth {
    param($Configuration, [int]$TimeoutSeconds)
    $urls = Get-PlanningUrls -Configuration $Configuration
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = 'Services indisponibles.'
    do {
        try {
            $containers = @(Invoke-PlanningCompose -Arguments @('ps', '--all', '--format', 'json') | ForEach-Object { $_ | ConvertFrom-Json })
            foreach ($service in $PlanningServices) {
                $container = $containers | Where-Object Service -eq $service | Select-Object -First 1
                if (!$container -or $container.State -ne 'running' -or $container.Health -ne 'healthy') {
                    throw "Le service $service n'est pas prêt."
                }
            }
            foreach ($uri in @("$($urls.Api)/api/tenants", "$($urls.Frontend)/", "$($urls.Frontend)/api/tenants")) {
                $response = Invoke-WebRequest -Uri $uri -UseBasicParsing -TimeoutSec 5
                if ($response.StatusCode -ne 200) { throw "Réponse inattendue pour $uri : $($response.StatusCode)." }
            }
            Write-Host "Planning prêt : interface $($urls.Frontend), API $($urls.Api)."
            return
        } catch {
            $lastError = $_.Exception.Message
            if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds 2 }
        }
    } while ((Get-Date) -lt $deadline)
    throw "Planning n'est pas prêt après $TimeoutSeconds secondes : $lastError"
}
