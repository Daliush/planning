import React from "react";
import type { Assignment, Violation } from "../../api/types";
import type { Shift, WorkforceOverview } from "./types";
import {
  ABSENCE_LABELS,
  dateLabel,
  duration,
  periodDays,
  shiftAssignments,
  time,
} from "./format";
import { Avatar, EmptyPanel, Icon, Status, WButton } from "./components";

export function PlanningView({
  overview,
  selectedDate,
  onDate,
  onAddShift,
  onEditShift,
  onDeleteShift,
}: {
  overview: WorkforceOverview;
  selectedDate: string;
  onDate: (date: string) => void;
  onAddShift: () => void;
  onEditShift: (shift: Shift) => void;
  onDeleteShift: (shift: Shift) => void;
}) {
  if (!overview.shifts.length)
    return (
      <EmptyPanel
        title={
          overview.tenant.profile === "RESTAURANT"
            ? "Préparez votre premier service"
            : "Définissez vos besoins de travail"
        }
        description={
          overview.tenant.profile === "RESTAURANT"
            ? "Indiquez une date, des horaires et les postes à couvrir. Votre équipe pourra ensuite être affectée."
            : "Ajoutez une plage de travail avec les horaires, les postes et le nombre de personnes nécessaires."
        }
        action={
          <WButton icon="plus" onClick={onAddShift}>
            {overview.tenant.profile === "RESTAURANT"
              ? "Ajouter un service"
              : "Ajouter une plage"}
          </WButton>
        }
      />
    );
  return overview.tenant.profile === "RESTAURANT" ? (
    <RestaurantPlanning
      overview={overview}
      selectedDate={selectedDate}
      onDate={onDate}
      onAddShift={onAddShift}
      onEditShift={onEditShift}
      onDeleteShift={onDeleteShift}
    />
  ) : (
    <CompanyPlanning
      overview={overview}
      onEditShift={onEditShift}
      onAddShift={onAddShift}
    />
  );
}

function DayStrip({
  overview,
  selectedDate,
  onDate,
}: {
  overview: WorkforceOverview;
  selectedDate: string;
  onDate: (date: string) => void;
}) {
  return (
    <div className="wf-day-strip" aria-label="Choisir une journée">
      {periodDays(overview.period.from, overview.period.to).map((date) => {
        const shifts = overview.shifts.filter((shift) => shift.date === date);
        const required = shifts.reduce(
          (sum, shift) =>
            sum + shift.roles.reduce((count, role) => count + role.quantity, 0),
          0,
        );
        const filled = shifts.reduce(
          (sum, shift) =>
            sum +
            shiftAssignments(overview, shift).filter(
              (assignment) => assignment.resourceId,
            ).length,
          0,
        );
        return (
          <button
            type="button"
            key={date}
            className={`wf-day ${date === selectedDate ? "is-selected" : ""}`}
            aria-pressed={date === selectedDate}
            onClick={() => onDate(date)}
          >
            <span>{dateLabel(date, { weekday: "short" })}</span>
            <strong>{dateLabel(date, { day: "numeric" })}</strong>
            <small>
              {shifts.length
                ? overview.assignments.length
                  ? `${filled}/${required} postes`
                  : `${shifts.length} service${shifts.length > 1 ? "s" : ""}`
                : "Repos"}
            </small>
            {required > 0 && overview.assignments.length > 0 && (
              <span
                className={`wf-day-dot ${filled < required ? "is-warning" : ""}`}
              />
            )}
          </button>
        );
      })}
    </div>
  );
}

function RestaurantPlanning(props: {
  overview: WorkforceOverview;
  selectedDate: string;
  onDate: (date: string) => void;
  onAddShift: () => void;
  onEditShift: (shift: Shift) => void;
  onDeleteShift: (shift: Shift) => void;
}) {
  const { overview, selectedDate } = props;
  const shifts = overview.shifts
    .filter((shift) => shift.date === selectedDate)
    .sort((a, b) => a.startTime.localeCompare(b.startTime));
  const absences = overview.absences.filter(
    (absence) => absence.from <= selectedDate && absence.to >= selectedDate,
  );
  return (
    <>
      <DayStrip
        overview={overview}
        selectedDate={selectedDate}
        onDate={props.onDate}
      />
      <div className="wf-day-heading">
        <h2>
          {dateLabel(selectedDate, {
            weekday: "long",
            day: "numeric",
            month: "long",
          })}
        </h2>
        <span>
          {shifts.length} service{shifts.length > 1 ? "s" : ""}
          {absences.length
            ? `, ${absences.length} absence${absences.length > 1 ? "s" : ""}`
            : ""}
        </span>
      </div>
      {absences.length > 0 && (
        <div className="wf-absence-line">
          <Icon name="absence" />
          <span>
            Absent{absences.length > 1 ? "s" : ""} :{" "}
            {absences
              .map(
                (absence) =>
                  overview.employees.find(
                    (employee) => employee.id === absence.resourceId,
                  )?.label ?? "Personne retirée",
              )
              .join(", ")}
          </span>
        </div>
      )}
      <div className="wf-service-list">
        {shifts.map((shift) => (
          <Service
            key={shift.id}
            overview={overview}
            shift={shift}
            onEdit={() => props.onEditShift(shift)}
            onDelete={() => props.onDeleteShift(shift)}
          />
        ))}
      </div>
      {!shifts.length && (
        <EmptyPanel
          title="Aucun service ce jour-là"
          description="Cette journée est libre. Ajoutez un service si votre établissement est ouvert."
          action={
            <WButton variant="secondary" icon="plus" onClick={props.onAddShift}>
              Ajouter un service
            </WButton>
          }
        />
      )}
    </>
  );
}

function Service({
  overview,
  shift,
  onEdit,
  onDelete,
}: {
  overview: WorkforceOverview;
  shift: Shift;
  onEdit: () => void;
  onDelete: () => void;
}) {
  const [expanded, setExpanded] = React.useState(true);
  const rolesId = React.useId();
  const assigned = shiftAssignments(overview, shift);
  const filled = assigned.filter((assignment) => assignment.resourceId).length;
  const required = shift.roles.reduce((sum, role) => sum + role.quantity, 0);
  const solved =
    overview.latestJob?.status === "DONE" && !overview.planOutdated;
  return (
    <section
      className={`wf-service ${expanded ? "is-expanded" : "is-collapsed"}`}
    >
      <header>
        <div className="wf-service-name">
          <span
            className={`wf-service-symbol ${Number(shift.startTime.slice(0, 2)) >= 16 ? "is-evening" : ""}`}
          >
            <Icon name="restaurant" />
          </span>
          <div>
            <h3>{shift.label}</h3>
            <p>
              {time(shift.startTime)} – {time(shift.endTime)}
              {shift.nextDay ? " (+1 jour)" : ""}
              <span>{duration(shift.durationMinutes)}</span>
            </p>
          </div>
        </div>
        <div className="wf-service-actions">
          <Status
            tone={
              solved ? (filled === required ? "good" : "warning") : "neutral"
            }
          >
            {solved
              ? `${filled} / ${required} postes couverts`
              : `${required} postes à attribuer`}
          </Status>
          <button
            type="button"
            className="wf-icon-button wf-service-toggle"
            onClick={() => setExpanded((current) => !current)}
            aria-expanded={expanded}
            aria-controls={rolesId}
            aria-label={`${expanded ? "Replier" : "Afficher"} les postes de ${shift.label}`}
          >
            <Icon name="chevron" />
          </button>
          <button
            className="wf-icon-button"
            onClick={onEdit}
            aria-label={`Modifier ${shift.label}`}
          >
            <Icon name="edit" />
          </button>
          <button
            className="wf-icon-button"
            onClick={onDelete}
            aria-label={`Supprimer ${shift.label}`}
          >
            <Icon name="trash" />
          </button>
        </div>
      </header>
      <div id={rolesId} className="wf-service-roles" hidden={!expanded}>
        {shift.roles.map((role, index) => {
          const roleAssignments = assigned.filter(
            (assignment) =>
              assignment.role === role.role && assignment.resourceId,
          );
          const vacancies = Math.max(0, role.quantity - roleAssignments.length);
          const eligible = overview.employees.filter(
            (employee) => !role.skill || employee.skills.includes(role.skill),
          );
          return (
            <div className="wf-role-row" key={`${role.role}-${index}`}>
              <div className="wf-role-heading">
                <strong>{role.role}</strong>
                <span>
                  {role.skill ? `Compétence : ${role.skill}` : "Toute l’équipe"}
                </span>
              </div>
              <div className="wf-assignees">
                {roleAssignments.map((assignment) => (
                  <div className="wf-assignee" key={assignment.id}>
                    <Avatar
                      label={assignment.resourceLabel ?? ""}
                      index={overview.employees.findIndex(
                        (employee) => employee.id === assignment.resourceId,
                      )}
                    />
                    <span>{assignment.resourceLabel}</span>
                    <Icon name="check" />
                  </div>
                ))}
                {Array.from({ length: vacancies }, (_, position) => (
                  <div
                    className={`wf-vacancy ${solved ? "is-unfilled" : ""}`}
                    key={position}
                  >
                    <span className="wf-vacancy-icon">
                      <Icon name={solved ? "alert" : "people"} />
                    </span>
                    <div>
                      <strong>{solved ? "Poste vacant" : "À attribuer"}</strong>
                      <small>
                        {!eligible.length
                          ? `Ajoutez une personne${role.skill ? ` avec la compétence ${role.skill}` : " à l’équipe"}`
                          : solved
                            ? "Consultez les points à vérifier ci-dessous"
                            : "Prêt pour la génération du planning"}
                      </small>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function CompanyPlanning({
  overview,
  onEditShift,
  onAddShift,
}: {
  overview: WorkforceOverview;
  onEditShift: (shift: Shift) => void;
  onAddShift: () => void;
}) {
  const [week, setWeek] = React.useState(0);
  const [skill, setSkill] = React.useState("");
  const [selectedDate, setSelectedDate] = React.useState(overview.period.from);
  const allDays = periodDays(overview.period.from, overview.period.to);
  const days = allDays.slice(week * 7, week * 7 + 7);
  const skills = [
    ...new Set(overview.employees.flatMap((employee) => employee.skills)),
  ];
  const employees = overview.employees.filter(
    (employee) => !skill || employee.skills.includes(skill),
  );
  const employeeShifts = (id: string, date: string) =>
    overview.shifts.filter(
      (shift) =>
        shift.date === date &&
        overview.assignments.some(
          (assignment) =>
            assignment.demandId === shift.id && assignment.resourceId === id,
        ),
    );
  const weekMinutes = (id: string) =>
    overview.shifts
      .filter(
        (shift) =>
          days.includes(shift.date) &&
          overview.assignments.some(
            (assignment) =>
              assignment.demandId === shift.id && assignment.resourceId === id,
          ),
      )
      .reduce((sum, shift) => sum + shift.durationMinutes, 0);
  const absence = (id: string, date: string) =>
    overview.absences.find(
      (value) =>
        value.resourceId === id && value.from <= date && value.to >= date,
    );
  return (
    <>
      <div className="wf-grid-toolbar">
        <div className="wf-week-nav">
          <WButton
            variant="quiet"
            icon="back"
            disabled={week === 0}
            aria-label="Semaine précédente"
            onClick={() => setWeek((current) => current - 1)}
          />
          <strong>
            {dateLabel(days[0])} – {dateLabel(days[days.length - 1])}
          </strong>
          <WButton
            variant="quiet"
            icon="chevron"
            disabled={(week + 1) * 7 >= allDays.length}
            aria-label="Semaine suivante"
            onClick={() => setWeek((current) => current + 1)}
          />
        </div>
        <label className="wf-filter">
          <span className="wf-sr-only">Filtrer par compétence</span>
          <select
            value={skill}
            onChange={(event) => setSkill(event.target.value)}
          >
            <option value="">Toutes les compétences</option>
            {skills.map((value) => (
              <option key={value}>{value}</option>
            ))}
          </select>
        </label>
      </div>
      <div className="wf-company-week">
        <table className="wf-schedule-table">
          <thead>
            <tr>
              <th scope="col">
                Équipe{" "}
                <span>
                  {employees.length} personne{employees.length > 1 ? "s" : ""}
                </span>
              </th>
              {days.map((date) => (
                <th key={date} scope="col">
                  <span>{dateLabel(date, { weekday: "short" })}</span>
                  <strong>{dateLabel(date, { day: "numeric" })}</strong>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {employees.map((employee, index) => (
              <tr key={employee.id}>
                <th scope="row">
                  <div className="wf-employee-cell">
                    <Avatar label={employee.label} index={index} />
                    <div>
                      <strong>{employee.label}</strong>
                      <span>
                        {duration(weekMinutes(employee.id))} attribuées /{" "}
                        {duration(employee.contractWeeklyMinutes)} au contrat
                      </span>
                    </div>
                  </div>
                </th>
                {days.map((date) => {
                  const dayAbsence = absence(employee.id, date);
                  const shifts = employeeShifts(employee.id, date);
                  return (
                    <td key={date}>
                      {dayAbsence ? (
                        <div className="wf-grid-absence">
                          <Icon name="absence" />
                          <span>{ABSENCE_LABELS[dayAbsence.reason]}</span>
                        </div>
                      ) : shifts.length ? (
                        shifts.map((shift) => (
                          <button
                            className={`wf-shift-block wf-shift-block--${index % 3}`}
                            key={shift.id}
                            onClick={() => onEditShift(shift)}
                          >
                            <strong>
                              {time(shift.startTime)} – {time(shift.endTime)}
                              {shift.nextDay ? " +1" : ""}
                            </strong>
                            <span>{shift.label}</span>
                          </button>
                        ))
                      ) : (
                        <span className="wf-rest">
                          {overview.assignments.length
                            ? "Repos"
                            : "À planifier"}
                        </span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
        {!employees.length && (
          <div className="wf-table-empty">
            {overview.employees.length
              ? "Aucune personne ne possède cette compétence."
              : "Ajoutez votre équipe pour afficher les horaires par personne."}
          </div>
        )}
      </div>
      <div className="wf-company-mobile">
        <DayStrip
          overview={overview}
          selectedDate={selectedDate}
          onDate={setSelectedDate}
        />
        <h2 className="wf-mobile-date">
          {dateLabel(selectedDate, {
            weekday: "long",
            day: "numeric",
            month: "long",
          })}
        </h2>
        {employees.map((employee, index) => {
          const dayAbsence = absence(employee.id, selectedDate);
          const shifts = employeeShifts(employee.id, selectedDate);
          return (
            <div className="wf-mobile-person" key={employee.id}>
              <div>
                <Avatar label={employee.label} index={index} />
                <strong>{employee.label}</strong>
              </div>
              {dayAbsence ? (
                <Status>{ABSENCE_LABELS[dayAbsence.reason]}</Status>
              ) : shifts.length ? (
                shifts.map((shift) => (
                  <button
                    className="wf-shift-block"
                    key={shift.id}
                    onClick={() => onEditShift(shift)}
                  >
                    <strong>
                      {time(shift.startTime)} – {time(shift.endTime)}
                    </strong>
                    <span>{shift.label}</span>
                  </button>
                ))
              ) : (
                <span className="wf-rest">
                  {overview.assignments.length ? "Repos" : "À planifier"}
                </span>
              )}
            </div>
          );
        })}
      </div>
      <div className="wf-needs-heading">
        <h2>Besoins à couvrir</h2>
        <span>Par plage de travail</span>
      </div>
      <div className="wf-company-needs">
        {overview.shifts
          .filter((shift) => days.includes(shift.date))
          .map((shift) => {
            const required = shift.roles.reduce(
              (sum, role) => sum + role.quantity,
              0,
            );
            const filled = shiftAssignments(overview, shift).filter(
              (assignment) => assignment.resourceId,
            ).length;
            return (
              <button
                key={shift.id}
                className="wf-need-row"
                onClick={() => onEditShift(shift)}
              >
                <span className="wf-need-date">
                  {dateLabel(shift.date, { weekday: "short", day: "numeric" })}
                </span>
                <span>
                  <strong>{shift.label}</strong>
                  <small>
                    {time(shift.startTime)} – {time(shift.endTime)}
                    {shift.nextDay ? " (+1 jour)" : ""}
                  </small>
                </span>
                <span className="wf-need-roles">
                  {shift.roles
                    .map((role) => `${role.quantity} ${role.role}`)
                    .join(", ")}
                </span>
                <Status
                  tone={
                    overview.latestJob?.status === "DONE" &&
                    !overview.planOutdated
                      ? filled === required
                        ? "good"
                        : "warning"
                      : "neutral"
                  }
                >
                  {filled} / {required}
                </Status>
                <Icon name="chevron" />
              </button>
            );
          })}
      </div>
      <WButton variant="quiet" icon="plus" onClick={onAddShift}>
        Ajouter une plage de travail
      </WButton>
    </>
  );
}

export function ResultNotes({ overview }: { overview: WorkforceOverview }) {
  const report = overview.report?.report;
  if (!report) return null;
  const unassigned = overview.assignments.filter(
    (assignment) => !assignment.resourceId,
  );
  const corrections = report.hardViolations.filter(
    (issue) =>
      issue.constraintId !== "booking.unassigned" || !unassigned.length,
  );
  const adjustments = report.softViolations
    .filter((issue) => issue.penalty > 0)
    .sort(
      (left, right) => adjustmentPriority(left) - adjustmentPriority(right),
    );
  if (!corrections.length && !adjustments.length && !unassigned.length)
    return (
      <div className="wf-result-clean">
        <Icon name="check" />
        <p>
          Les postes sont couverts et les règles d’organisation sont respectées.
        </p>
      </div>
    );
  return (
    <section className="wf-result-notes">
      <h2>
        <Icon
          name={unassigned.length || corrections.length ? "alert" : "check"}
        />
        {unassigned.length || corrections.length
          ? "Points à vérifier"
          : "Ajustements proposés"}
      </h2>
      <p>
        {unassigned.length
          ? `${unassigned.length} poste${unassigned.length > 1 ? "s restent vacants" : " reste vacant"}. `
          : "Tous les postes sont couverts. "}
        {corrections.length
          ? "Certaines règles demandent une correction."
          : adjustments.length
            ? "Les préférences ci-dessous peuvent encore être améliorées."
            : "Vérifiez les absences, les compétences et les besoins."}
      </p>
      {!!(unassigned.length || corrections.length) && (
        <ul>
          {unassigned.slice(0, 5).map((assignment) => (
            <Unfilled
              key={assignment.id}
              assignment={assignment}
              overview={overview}
            />
          ))}
          {corrections
            .slice(0, Math.max(0, 5 - unassigned.length))
            .map((issue, index) => (
              <li key={`${issue.constraintId}-${index}`}>
                <Status tone="warning">À corriger</Status>
                <span>{businessMessage(issue.message)}</span>
              </li>
            ))}
        </ul>
      )}
      {unassigned.length + corrections.length > 5 && (
        <details className="wf-adjustments">
          <summary>
            Voir les autres points à corriger{" "}
            <span>{unassigned.length + corrections.length - 5}</span>
          </summary>
          <ul>
            {unassigned.slice(5).map((assignment) => (
              <Unfilled
                key={assignment.id}
                assignment={assignment}
                overview={overview}
              />
            ))}
            {corrections
              .slice(Math.max(0, 5 - unassigned.length))
              .map((issue, index) => (
                <li key={`${issue.constraintId}-${index}`}>
                  <Status tone="warning">À corriger</Status>
                  <span>{businessMessage(issue.message)}</span>
                </li>
              ))}
          </ul>
        </details>
      )}
      {!!adjustments.length && (
        <>
          <ul className="wf-adjustment-preview">
            {adjustments.slice(0, 3).map((issue, index) => (
              <li key={`${issue.constraintId}-${index}`}>
                <Status>{adjustmentLabel(issue.constraintId, overview)}</Status>
                <span>{businessMessage(issue.message)}</span>
              </li>
            ))}
          </ul>
          {adjustments.length > 3 && (
            <details className="wf-adjustments">
              <summary>
                Voir les autres ajustements{" "}
                <span>{adjustments.length - 3}</span>
              </summary>
              {groupAdjustments(adjustments.slice(3)).map(([type, issues]) => (
                <div className="wf-adjustment-group" key={type}>
                  <h3>
                    {adjustmentLabel(type, overview)}{" "}
                    <span>{issues.length}</span>
                  </h3>
                  <ul>
                    {issues.map((issue, index) => (
                      <li key={`${type}-${index}`}>
                        <span>{businessMessage(issue.message)}</span>
                      </li>
                    ))}
                  </ul>
                </div>
              ))}
            </details>
          )}
        </>
      )}
    </section>
  );
}
function businessMessage(message: string): string {
  return message
    .replace(/sur l['’]horizon/gi, "sur cette période")
    .replace(/ porte /g, " totalise ");
}
function adjustmentPriority(issue: Violation): number {
  return issue.constraintId === "load.contract"
    ? 0
    : issue.constraintId === "load.balance"
      ? 2
      : 1;
}
function adjustmentLabel(type: string, overview: WorkforceOverview): string {
  const labels: Record<string, string> = {
    "load.contract": "Heures au contrat",
    "load.balance": "Répartition du travail",
    "fairness.weekend": "Répartition des week-ends",
    "resource.discouraged_slot": "Créneaux déconseillés",
    "load.min": "Heures minimum",
  };
  return (
    labels[type] ?? overview.report?.constraintLabels[type] ?? "Organisation"
  );
}
function groupAdjustments(issues: Violation[]): [string, Violation[]][] {
  return Object.entries(
    issues.reduce<Record<string, Violation[]>>((groups, issue) => {
      (groups[issue.constraintId] ??= []).push(issue);
      return groups;
    }, {}),
  );
}
function Unfilled({
  assignment,
  overview,
}: {
  assignment: Assignment;
  overview: WorkforceOverview;
}) {
  const shift = overview.shifts.find(
    (value) => value.id === assignment.demandId,
  );
  const role = shift?.roles.find((value) => value.role === assignment.role);
  const eligible = overview.employees.filter(
    (employee) => !role?.skill || employee.skills.includes(role.skill),
  );
  const reason = !eligible.length
    ? `Aucune personne ne possède la compétence ${role?.skill ?? "nécessaire"}.`
    : "Vérifiez les absences, les horaires et les limites de travail.";
  return (
    <li>
      <Status tone="warning">Poste vacant</Status>
      <span>
        <strong>
          {shift?.label ?? assignment.demandLabel} : {assignment.role}
        </strong>
        {shift && `, ${dateLabel(shift.date)} à ${time(shift.startTime)}`}
        <small>{reason}</small>
      </span>
    </li>
  );
}
