param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'

# Données fictives créées dans un espace isolé, uniquement sur une base locale/de test.
function Call-Api([string]$Method, [string]$Path, $Body = $null) {
    $parameters = @{Method=$Method; Uri="$BaseUrl/api/$Path"}
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = $Body | ConvertTo-Json -Depth 12
    }
    Invoke-RestMethod @parameters
}
function Assert-True([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}
function Solve([string]$ScenarioId) {
    $job = Call-Api POST "scenarios/$ScenarioId/solve-jobs" @{timeLimitSeconds=3}
    $deadline = (Get-Date).AddSeconds(30)
    do {
        Start-Sleep -Milliseconds 400
        $job = Call-Api GET "solve-jobs/$($job.id)"
        if ((Get-Date) -gt $deadline) { throw 'Le calcul a dépassé le délai de validation.' }
    } while ($job.status -in @('QUEUED','RUNNING'))
    Assert-True ($job.status -eq 'DONE') "Calcul en échec : $($job.errorCode) $($job.errorDetail)"
    $report = Call-Api GET "solve-jobs/$($job.id)/report"
    Assert-True ($job.hardScore -eq $report.report.hardScore -and $job.softScore -eq $report.report.softScore) 'Score et rapport divergent.'
    Call-Api GET "workforce/scenarios/$ScenarioId/overview"
}

$workspace = Call-Api POST 'workforce/workspaces' @{
    label='Validation contrat strict'; profile='COMPANY'; from='2026-10-05'; to='2026-10-11'; demo=$false
}
$id = $workspace.scenarioId
$employeeBody = @{label='Camille test'; skills=@('support'); contractWeeklyMinutes=2100;
    overtimeWeeklyMinutes=0; absenceDailyMinutes=420; availability=@()}
$employee = Call-Api POST "workforce/scenarios/$id/employees" $employeeBody
foreach ($day in 5..9) {
    Call-Api POST "workforce/scenarios/$id/shifts" @{
        label="Support $day"; date="2026-10-$('{0:00}' -f $day)"; startTime='09:00'; endTime='16:00'; nextDay=$false
        roles=@(@{role='Support'; quantity=1; skill='support'})
    } | Out-Null
}
$overview = Solve $id
Assert-True ($overview.latestJob.feasible -and $overview.contractWeeks[0].assignedMinutes -eq 2100) 'Le contrat de 35 h exact est mal traité.'
Assert-True ($overview.contractWeeks[0].missingMinutes -eq 0 -and $overview.contractWeeks[0].overtimeMinutes -eq 0) 'Le bilan hebdomadaire de 35 h est erroné.'

Call-Api POST "workforce/scenarios/$id/shifts" @{
    label='Renfort samedi'; date='2026-10-10'; startTime='09:00'; endTime='12:00'; nextDay=$false
    roles=@(@{role='Support'; quantity=1; skill='support'})
} | Out-Null
$overview = Solve $id
Assert-True (!$overview.latestJob.feasible -and $overview.contractWeeks[0].assignedMinutes -eq 2100) 'Le moteur dépasse 35 h sans autorisation.'
Assert-True ($overview.kpis.unassignedPositions -eq 1) 'Le renfort non autorisé devrait rester vacant.'

$employeeBody.overtimeWeeklyMinutes = 180
Call-Api PUT "workforce/scenarios/$id/employees/$($employee.id)" $employeeBody | Out-Null
$overview = Solve $id
Assert-True ($overview.latestJob.feasible -and $overview.contractWeeks[0].assignedMinutes -eq 2280) 'Les 3 h supplémentaires autorisées ne permettent pas 38 h.'
Assert-True ($overview.contractWeeks[0].overtimeMinutes -eq 180 -and $overview.contractWeeks[0].maximumMinutes -eq 2280) 'Le bilan des heures supplémentaires est erroné.'

Call-Api POST "workforce/scenarios/$id/absences" @{
    resourceId=$employee.id; from='2026-10-05'; to='2026-10-05'; reason='SICKNESS'; note='Données fictives de validation'
} | Out-Null
$overview = Solve $id
$week = $overview.contractWeeks[0]
Assert-True ($week.absenceMinutes -eq 420 -and $week.dueMinutes -eq 1680 -and $week.maximumMinutes -eq 1860) 'Une journée de 7 h ne réduit pas la cible à 28 h avec un plafond de 31 h.'
Assert-True ($week.assignedMinutes -eq 1860 -and $week.missingMinutes -eq 0) 'Le contrat réduit et la marge supplémentaire ne sont pas respectés.'
Assert-True ($overview.assignments.Where({$_.resourceId -eq $employee.id -and $_.date -eq '2026-10-05'}).Count -eq 0) 'Une personne absente a été planifiée.'

$employeeBody.availability = @(@{effect='UNAVAILABLE'; dayInCycle=3; startTime='11:00'; endTime='12:00'; priority=0},
    @{effect='UNAVAILABLE'; dayInCycle=5; startTime='22:00'; endTime='02:00'; priority=0})
Call-Api PUT "workforce/scenarios/$id/employees/$($employee.id)" $employeeBody | Out-Null
$overview = Solve $id
$week = $overview.contractWeeks[0]
Assert-True ($week.dueMinutes -eq 1680 -and $week.missingMinutes -gt 0 -and !$overview.latestJob.feasible) 'Une indisponibilité horaire a réduit le contrat ou caché un manque.'
Assert-True ($overview.assignments.Where({$_.resourceId -eq $employee.id -and $_.date -eq '2026-10-08'}).Count -eq 0) 'La plage horaire indisponible a été ignorée.'
Assert-True (($overview.employees[0].availability | Where-Object startTime -eq '22:00:00').endTime -eq '02:00:00') 'La plage nocturne a été altérée.'

@{scenarioId=$id; contractMinutes=$week.contractMinutes; dueMinutes=$week.dueMinutes;
    maximumMinutes=$week.maximumMinutes; assignedMinutes=$week.assignedMinutes; missingMinutes=$week.missingMinutes} | ConvertTo-Json
Write-Output 'Validation réussie : contrat strict, supplément autorisé, absence déduite, indisponibilités horaires et nocturnes.'
