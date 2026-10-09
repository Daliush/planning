import type { ContractWeek, WorkforceOverview } from "./types";
import { dateLabel, duration } from "./format";
import { Icon, Status } from "./components";

export function WeeklyContractLabel({
  contract,
  solved,
}: {
  contract: ContractWeek;
  solved: boolean;
}) {
  return (
    <div className="wf-weekly-contract">
      <span>
        {solved
          ? `${duration(contract.assignedMinutes)} attribuées / ${duration(contract.dueMinutes)} dues`
          : `${duration(contract.dueMinutes)} à attribuer`}
      </span>
      {contract.absenceMinutes > 0 && (
        <span>{duration(contract.absenceMinutes)} déduites pour absence</span>
      )}
      {solved && contract.missingMinutes > 0 && (
        <Status tone="warning">
          {duration(contract.missingMinutes)} manquantes
        </Status>
      )}
      {solved && contract.overtimeMinutes > 0 && (
        <Status>{duration(contract.overtimeMinutes)} supp.</Status>
      )}
    </div>
  );
}

export function ContractBalances({
  overview,
}: {
  overview: WorkforceOverview;
}) {
  if (
    overview.latestJob?.status !== "DONE" ||
    overview.planOutdated ||
    !overview.contractWeeks?.length
  )
    return null;
  const differences = overview.contractWeeks
    .filter((week) => week.missingMinutes > 0 || week.overtimeMinutes > 0)
    .sort((left, right) => right.missingMinutes - left.missingMinutes);
  const missing = overview.contractWeeks.reduce(
    (sum, week) => sum + week.missingMinutes,
    0,
  );
  const overtime = overview.contractWeeks.reduce(
    (sum, week) => sum + week.overtimeMinutes,
    0,
  );
  if (!differences.length)
    return (
      <div className="wf-result-clean">
        <Icon name="check" />
        <p>Les contrats hebdomadaires sont atteints.</p>
      </div>
    );
  return (
    <section className="wf-contract-results">
      <h2>Contrats hebdomadaires</h2>
      <p>
        {missing > 0
          ? `${duration(missing)} de contrat restent à couvrir. `
          : "Les contrats sont atteints. "}
        {overtime > 0
          ? `${duration(overtime)} supplémentaires attribuées.`
          : ""}
      </p>
      <ContractRows weeks={differences.slice(0, 5)} overview={overview} />
      {differences.length > 5 && (
        <details className="wf-adjustments">
          <summary>
            Voir les autres contrats <span>{differences.length - 5}</span>
          </summary>
          <ContractRows weeks={differences.slice(5)} overview={overview} />
        </details>
      )}
    </section>
  );
}
function ContractRows({
  weeks,
  overview,
}: {
  weeks: ContractWeek[];
  overview: WorkforceOverview;
}) {
  return (
    <div className="wf-contract-list">
      {weeks.map((week) => (
        <div
          className="wf-contract-row"
          key={`${week.resourceId}-${week.from}`}
        >
          <div>
            <strong>
              {overview.employees.find(
                (employee) => employee.id === week.resourceId,
              )?.label ?? "Personne retirée"}
            </strong>
            <small>
              {dateLabel(week.from)} – {dateLabel(week.to)}
            </small>
          </div>
          <div>
            <strong>{duration(week.dueMinutes)}</strong>
            <small>dues après absences</small>
          </div>
          <div>
            <strong>{duration(week.assignedMinutes)}</strong>
            <small>attribuées</small>
          </div>
          <div className="wf-contract-indicators">
            {week.missingMinutes > 0 && (
              <Status tone="warning">
                {duration(week.missingMinutes)} manquantes
              </Status>
            )}
            {week.overtimeMinutes > 0 && (
              <Status
                tone={
                  week.assignedMinutes > week.maximumMinutes
                    ? "warning"
                    : "neutral"
                }
              >
                {duration(week.overtimeMinutes)} supp.
              </Status>
            )}
            {week.assignedMinutes > week.maximumMinutes && (
              <Status tone="warning">Plafond dépassé</Status>
            )}
          </div>
          <small className="wf-contract-limit">
            Plafond : {duration(week.maximumMinutes)}
          </small>
        </div>
      ))}
    </div>
  );
}
