param(
    [string]$BaseUrl = 'http://127.0.0.1:8081',
    [string]$WeekStart,
    [ValidateRange(3, 120)][int]$TimeLimitSeconds = 15
)
$ErrorActionPreference = 'Stop'
if (!$WeekStart) {
    $today = (Get-Date).Date
    $WeekStart = $today.AddDays(7 - (([int]$today.DayOfWeek + 6) % 7)).ToString('yyyy-MM-dd')
}
function Assert-Demo([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Solve-Demo([string]$ScenarioId) {
    $job = Invoke-RestMethod -Uri "$BaseUrl/api/scenarios/$ScenarioId/solve-jobs" -Method Post -Headers @{ Origin = $BaseUrl.TrimEnd('/') } -ContentType 'application/json' -Body (
        @{ timeLimitSeconds = $TimeLimitSeconds } | ConvertTo-Json)
    $deadline = (Get-Date).AddSeconds($TimeLimitSeconds + 30)
    do {
        Start-Sleep -Milliseconds 500
        $job = Invoke-RestMethod "$BaseUrl/api/solve-jobs/$($job.id)"
        if ((Get-Date) -gt $deadline) { throw 'Délai de résolution dépassé.' }
    } while ($job.status -in @('QUEUED', 'RUNNING'))
    Assert-Demo ($job.status -eq 'DONE') "Échec du calcul : $($job.errorCode) $($job.errorDetail)"
    $report = Invoke-RestMethod "$BaseUrl/api/solve-jobs/$($job.id)/report"
    Assert-Demo ($job.hardScore -eq $report.report.hardScore -and $job.softScore -eq $report.report.softScore) 'Le score et son explication divergent.'
    return Invoke-RestMethod "$BaseUrl/api/workforce/scenarios/$ScenarioId/overview"
}

# Ce contrôle crée uniquement des jobs dans les trois espaces fictifs du seed.
# Une démo modifiée peut ne plus avoir les résultats attendus ; elle n'est pas réinitialisée.
$cases = @(
    @{ Profile = 'RESTAURANT'; Label = 'Démo réaliste — Le Comptoir — Équilibré'; Positions = 61; Overtime = 180; Worker = 'Hugo Robert' },
    @{ Profile = 'COMPANY'; Label = 'Démo réaliste — Atelier Horizon — Équilibré'; Positions = 61; Overtime = 120; Worker = 'Jules Garcia' },
    @{ Profile = 'RESTAURANT'; Label = 'Démo réaliste — Le Comptoir — Deux tensions'; Positions = 62; Overtime = 0; Worker = 'Hugo Robert' }
)
foreach ($example in $cases) {
    $spaces = Invoke-RestMethod "$BaseUrl/api/workforce/workspaces?profile=$($example.Profile)"
    $space = @($spaces | Where-Object { $_.label -eq "$($example.Label) — $WeekStart" -and $_.from -eq $WeekStart })
    Assert-Demo ($space.Count -eq 1) "Démo absente : $($example.Label). Charger seed-demo.ps1 pour la semaine $WeekStart."
    $overview = Solve-Demo $space[0].scenarioId
    Assert-Demo ($overview.employees.Count -eq 7) 'La démo attend sept employés.'
    Assert-Demo ($overview.contractWeeks.Count -eq 7) 'La période doit avoir une seule semaine complète par employé.'
    Assert-Demo ($overview.kpis.requiredPositions -eq $example.Positions) 'Le nombre de postes ne correspond pas au jeu de référence.'
    $worker = $overview.employees | Where-Object label -eq $example.Worker
    $workerWeek = $overview.contractWeeks | Where-Object resourceId -eq $worker.id
    Assert-Demo ($workerWeek.overtimeMinutes -eq $example.Overtime) 'Le renfort ne respecte pas les heures supplémentaires prévues.'
    foreach ($week in $overview.contractWeeks) {
        Assert-Demo ($week.assignedMinutes -le $week.maximumMinutes) 'Un plafond individuel est dépassé.'
    }
    $absent = @($overview.contractWeeks | Where-Object absenceMinutes -gt 0)
    Assert-Demo ($absent.Count -eq 1 -and $absent[0].absenceMinutes -eq 420 -and $absent[0].dueMinutes -eq 1680) 'La journée d''absence doit laisser 28 h dues à la personne en contrat 35 h.'
    $missing = @($overview.contractWeeks | Where-Object missingMinutes -gt 0)
    if ($example.Label -like '*Deux tensions') {
        Assert-Demo (!$overview.latestJob.feasible -and $overview.latestJob.hardScore -eq -32 -and $overview.kpis.unassignedPositions -eq 2) 'La variante doit laisser deux postes vacants et 30 minutes de déficit contractuel, sans autre violation HARD.'
        $manager = $overview.employees | Where-Object label -eq 'Élodie Martin'
        Assert-Demo ($missing.Count -eq 1 -and $missing[0].resourceId -eq $manager.id -and $missing[0].missingMinutes -eq 30) 'Élodie doit récupérer 3 h au banquet et rester la seule personne avec 30 minutes de contrat manquantes.'
        $vacancies = @($overview.assignments | Where-Object { !$_.resourceId })
        Assert-Demo ($vacancies.Count -eq 2 -and @($vacancies | Where-Object role -eq 'Pâtisserie').Count -eq 1 -and @($vacancies | Where-Object role -eq 'Responsable').Count -eq 1) 'Les postes vacants doivent être la pâtisserie et la responsabilité du dimanche soir.'
    } else {
        Assert-Demo ($overview.latestJob.feasible -and $overview.kpis.unassignedPositions -eq 0 -and $missing.Count -eq 0) 'La référence doit couvrir tous les postes et respecter tous les contrats.'
    }
    Write-Host "$($example.Label) : HARD=$($overview.latestJob.hardScore), postes vacants=$($overview.kpis.unassignedPositions), contrats incomplets=$($missing.Count)."
}
Write-Host 'Données réalistes validées : contrats atteints, absences déduites, suppléments autorisés et deux tensions identifiées.'
