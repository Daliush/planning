param([string]$BaseUrl = 'http://localhost:8081')
$ErrorActionPreference = 'Stop'

# Exécuter uniquement sur une base locale/de test : crée des espaces de démonstration isolés.
function Call-Api([string]$Method, [string]$Path, $Body = $null) {
    $parameters = @{ Method=$Method; Uri="$BaseUrl/api/$Path" }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json; charset=utf-8'
        $parameters.Body = $Body | ConvertTo-Json -Depth 12
    }
    $response = Invoke-RestMethod @parameters
    foreach ($entry in $response) { Write-Output $entry }
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
    Assert-True ($job.hardScore -eq $report.report.hardScore -and $job.softScore -eq $report.report.softScore) 'Score et explications divergent.'
    Call-Api GET "workforce/scenarios/$ScenarioId/overview"
}

$created = @()
foreach ($profile in @('RESTAURANT','COMPANY')) {
    $workspace = Call-Api POST 'workforce/workspaces' @{
        label="Validation $profile"; profile=$profile; from='2026-10-05'; to='2026-10-11'; demo=$true
    }
    $overview = Solve $workspace.scenarioId
    Assert-True ($overview.kpis.assignedPositions -gt 0) "Aucun poste affecté dans $profile."
    foreach ($assignment in $overview.assignments) {
        $shift = $overview.shifts | Where-Object id -eq $assignment.demandId
        Assert-True ($assignment.date -eq $shift.date) 'Le service a changé de date.'
        foreach ($absence in $overview.absences | Where-Object resourceId -eq $assignment.resourceId) {
            $start = [datetime]::Parse("$($assignment.date)T$($assignment.startTime)")
            $end = [datetime]::Parse("$($assignment.endDate)T$($assignment.endTime)")
            $absenceFrom = [datetime]::Parse($absence.from)
            $absenceTo = [datetime]::Parse($absence.to).AddDays(1)
            Assert-True (!($start -lt $absenceTo -and $absenceFrom -lt $end)) 'Un employé absent a été affecté.'
        }
    }
    $created += @{profile=$profile; scenarioId=$workspace.scenarioId; coverage=$overview.kpis.coveragePercent}
}

$night = Call-Api POST 'workforce/workspaces' @{
    label='Validation service de nuit'; profile='RESTAURANT'; from='2026-10-08'; to='2026-10-10'; demo=$false
}
$id = $night.scenarioId
$employee = Call-Api POST "workforce/scenarios/$id/employees" @{
    label='Employé de test'; skills=@('bar'); contractWeeklyMinutes=2100; availability=@()
}
$shift = Call-Api POST "workforce/scenarios/$id/shifts" @{
    label='Fermeture'; date='2026-10-08'; startTime='22:00'; endTime='02:00'; nextDay=$true
    roles=@(@{role='Bar'; quantity=1; skill='bar'})
}
$overview = Solve $id
Assert-True ($overview.kpis.assignedPositions -eq 1) 'Le service de nuit reste vide.'
Assert-True ($overview.assignments[0].endDate -eq '2026-10-09' -and $overview.assignments[0].endTime -eq '02:00:00') 'La fin de nuit est erronée.'
$absence = Call-Api POST "workforce/scenarios/$id/absences" @{
    resourceId=$employee.id; from='2026-10-09'; to='2026-10-09'; reason='SICKNESS'; note='Démonstration fictive'
}
$outdated = Call-Api GET "workforce/scenarios/$id/overview"
Assert-True ($outdated.planOutdated -and $outdated.assignments.Count -eq 0) "L'ancien résultat reste présenté après une absence."
$overview = Solve $id
Assert-True ($overview.kpis.unassignedPositions -eq 1 -and $overview.kpis.assignedPositions -eq 0) "L'absence du lendemain ne bloque pas le service de nuit."
$shortShift = Call-Api POST "workforce/scenarios/$id/shifts" @{
    label='Service court pendant une absence'; date='2026-10-09'; startTime='09:00'; endTime='09:30'; nextDay=$false
    roles=@(@{role='Bar'; quantity=1; skill='bar'})
}
$overview = Solve $id
Assert-True ($overview.kpis.unassignedPositions -eq 2 -and $overview.kpis.assignedPositions -eq 0) 'Un service court reste attribue pendant une absence.'
$created += @{profile='NIGHT_ABSENCE'; scenarioId=$id; unassigned=$overview.kpis.unassignedPositions}

$tenants = Call-Api GET 'tenants'
$school = $tenants | Where-Object profile -eq 'SCHOOL' | Select-Object -First 1
$schoolScenarios = Call-Api GET "tenants/$($school.id)/scenarios"
$schoolScenario = $schoolScenarios | Select-Object -First 1
Assert-True ($null -ne $schoolScenario) 'Le scénario scolaire existant manque.'
$schoolJob = Call-Api POST "scenarios/$($schoolScenario.id)/solve-jobs" @{timeLimitSeconds=2}
$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 400
    $schoolJob = Call-Api GET "solve-jobs/$($schoolJob.id)"
    if ((Get-Date) -gt $deadline) { throw 'La validation scolaire a dépassé le délai.' }
} while ($schoolJob.status -in @('QUEUED','RUNNING'))
Assert-True ($schoolJob.status -eq 'DONE') 'La résolution scolaire a régressé.'
$created | ConvertTo-Json -Depth 4
Write-Output 'Validation métier réussie : restaurant, entreprise, nuit, maladie, résultat obsolète et école.'
