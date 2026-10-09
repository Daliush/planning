param(
    [string]$ProjectName,
    [string]$EnvFile,
    [ValidateRange(0, 65535)][int]$DatabasePort = 0,
    [ValidateRange(0, 65535)][int]$ApiPort = 0,
    [ValidateRange(0, 65535)][int]$FrontendPort = 0,
    [string]$WeekStart
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'runtime.ps1')
if (!$WeekStart) {
    $today = (Get-Date).Date
    $daysSinceMonday = ([int]$today.DayOfWeek + 6) % 7
    $WeekStart = $today.AddDays(7 - $daysSinceMonday).ToString('yyyy-MM-dd')
}
$monday = [datetime]::ParseExact($WeekStart, 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
if ($monday.DayOfWeek -ne [DayOfWeek]::Monday) { throw '-WeekStart doit être un lundi au format AAAA-MM-JJ.' }

$previousValues = Set-PlanningPortOverrides -DatabasePort $DatabasePort -ApiPort $ApiPort -FrontendPort $FrontendPort
$previousEncoding = $OutputEncoding
try {
    $configuration = Get-PlanningConfiguration
    $containerIds = @(Invoke-PlanningCompose -Arguments @('ps', '--status', 'running', '--quiet', 'planning-db'))
    if ($containerIds.Count -ne 1) { throw 'Démarrer la base Planning avant de charger la démonstration.' }
    $database = $configuration.services.'planning-db'.environment.POSTGRES_DB
    $username = $configuration.services.'planning-db'.environment.POSTGRES_USER
    $sqlFile = Join-Path $PlanningRoot 'database/seeds/workforce-realistic.sql'
    $OutputEncoding = [Text.UTF8Encoding]::new($false)
    Get-Content -LiteralPath $sqlFile -Raw -Encoding UTF8 |
        & docker exec -i $containerIds[0] psql -X -U $username -d $database -v ON_ERROR_STOP=1 -v "week_start=$WeekStart" -f -
    if ($LASTEXITCODE -ne 0) { throw 'Le chargement SQL a échoué. Consulter l''erreur PostgreSQL ci-dessus.' }
    Write-Host "Démonstration prête pour la semaine du $WeekStart. Les espaces déjà présents sont conservés."
} finally {
    $OutputEncoding = $previousEncoding
    Restore-PlanningEnvironment -PreviousValues $previousValues
}
