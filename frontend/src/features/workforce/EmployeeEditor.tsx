import React from "react";
import type { Availability } from "../../api/types";
import { workforceApi } from "../../api/workforce";
import type { Employee, WorkforceOverview } from "./types";
import { duration, humanError, time } from "./format";
import {
  EditorDialog,
  ErrorNotice,
  FormField,
  Icon,
  WButton,
} from "./components";

type AvailabilityInput = Omit<Availability, "id">;
const weekdays = [
  "Lundi",
  "Mardi",
  "Mercredi",
  "Jeudi",
  "Vendredi",
  "Samedi",
  "Dimanche",
];

function editableUnavailability(rule: Availability): boolean {
  return (
    rule.effect === "UNAVAILABLE" &&
    (rule.dayInCycle === null ||
      (rule.dayInCycle >= 0 && rule.dayInCycle <= 6)) &&
    ((!rule.startTime && !rule.endTime) || (!!rule.startTime && !!rule.endTime))
  );
}
const suggestedAbsenceMinutes = (contractMinutes: number) =>
  Math.min(1440, Math.round(contractMinutes / 5 / 30) * 30);

export function EmployeeEditor({
  overview,
  employee,
  onClose,
  onSaved,
}: {
  overview: WorkforceOverview;
  employee?: Employee;
  onClose: () => void;
  onSaved: () => Promise<void>;
}) {
  const [label, setLabel] = React.useState(employee?.label ?? "");
  const [skills, setSkills] = React.useState(employee?.skills.join(", ") ?? "");
  const [hours, setHours] = React.useState(
    (employee?.contractWeeklyMinutes ?? 2100) / 60,
  );
  const [overtimeHours, setOvertimeHours] = React.useState(
    (employee?.overtimeWeeklyMinutes ?? 0) / 60,
  );
  const [absenceHours, setAbsenceHours] = React.useState(
    (employee?.absenceDailyMinutes ??
      suggestedAbsenceMinutes(employee?.contractWeeklyMinutes ?? 2100)) / 60,
  );
  const absenceEdited = React.useRef(!!employee);
  const [unavailability, setUnavailability] = React.useState<
    AvailabilityInput[]
  >(() =>
    (employee?.availability ?? [])
      .filter(editableUnavailability)
      .map(({ id: _id, ...rule }) => rule),
  );
  const retained = (employee?.availability ?? [])
    .filter((rule) => !editableUnavailability(rule))
    .map(({ id: _id, ...rule }) => rule);
  const savedId = React.useRef(employee?.id);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();
  const suggestion =
    overview.tenant.profile === "RESTAURANT"
      ? "Cuisine, Salle"
      : "Accueil, Support";

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    const parsedSkills = [
      ...new Set(
        skills
          .split(",")
          .map((skill) => skill.trim())
          .filter(Boolean),
      ),
    ];
    if (!parsedSkills.length) {
      setError(
        "Ajoutez au moins une compétence pour pouvoir affecter cette personne.",
      );
      return;
    }
    if (hours + overtimeHours > 168) {
      setError(
        "Le contrat et les heures supplémentaires ne peuvent pas dépasser 168 heures par semaine.",
      );
      return;
    }
    if (
      unavailability.some(
        (rule) =>
          rule.startTime &&
          rule.endTime &&
          time(rule.startTime) === time(rule.endTime),
      )
    ) {
      setError(
        "Une plage d’indisponibilité doit avoir deux heures différentes. Cochez « Journée entière » pour bloquer tout le jour.",
      );
      return;
    }
    setSaving(true);
    try {
      const saved = await workforceApi.saveEmployee(
        overview.scenario.id,
        {
          label: label.trim(),
          skills: parsedSkills,
          contractWeeklyMinutes: Math.round(hours * 60),
          overtimeWeeklyMinutes: Math.round(overtimeHours * 60),
          absenceDailyMinutes: Math.round(absenceHours * 60),
          availability: [
            ...retained,
            ...unavailability.map((rule) => ({
              ...rule,
              startTime: rule.startTime ? time(rule.startTime) : null,
              endTime: rule.endTime ? time(rule.endTime) : null,
            })),
          ],
        },
        savedId.current,
      );
      savedId.current = saved.id;
      await onSaved();
      onClose();
    } catch (cause) {
      setError(humanError(cause));
    } finally {
      setSaving(false);
    }
  }
  return (
    <EditorDialog
      title={employee ? `Modifier ${employee.label}` : "Ajouter une personne"}
      description="Définissez ses compétences, ses heures de travail et ses indisponibilités."
      onClose={onClose}
    >
      <form onSubmit={save} className="wf-form">
        {error && <ErrorNotice message={error} />}
        <FormField label="Nom et prénom">
          <input
            autoFocus
            value={label}
            onChange={(event) => setLabel(event.target.value)}
            required
            maxLength={120}
            placeholder="Camille Martin"
          />
        </FormField>
        <FormField
          label="Compétences"
          hint="Séparez les compétences par une virgule. Reprenez les mêmes noms dans vos besoins de personnel."
        >
          <input
            value={skills}
            onChange={(event) => setSkills(event.target.value)}
            placeholder={suggestion}
            required
          />
        </FormField>
        <fieldset className="wf-fieldset wf-contract-fields">
          <legend>Contrat hebdomadaire obligatoire</legend>
          <p>
            Ces heures doivent être atteintes chaque semaine, après déduction
            des absences. Les indisponibilités ne diminuent pas le contrat.
          </p>
          <div className="wf-form-row">
            <FormField label="Heures au contrat">
              <div className="wf-input-unit">
                <input
                  type="number"
                  min={0}
                  max={168}
                  step={0.5}
                  value={hours}
                  onChange={(event) => {
                    const value = Number(event.target.value);
                    setHours(value);
                    if (!absenceEdited.current)
                      setAbsenceHours(suggestedAbsenceMinutes(value * 60) / 60);
                  }}
                  required
                />
                <span>h / semaine</span>
              </div>
            </FormField>
            <FormField label="Heures supplémentaires maximum">
              <div className="wf-input-unit">
                <input
                  type="number"
                  min={0}
                  max={168 - hours}
                  step={0.5}
                  value={overtimeHours}
                  onChange={(event) =>
                    setOvertimeHours(Number(event.target.value))
                  }
                  required
                />
                <span>h / semaine</span>
              </div>
            </FormField>
          </div>
          <p className="wf-form-summary">
            Maximum avant déduction des absences :{" "}
            {duration(Math.round((hours + overtimeHours) * 60))} par semaine.
          </p>
        </fieldset>
        <FormField
          label="Heures déduites par journée d’absence"
          hint="Chaque date enregistrée dans Absences déduit cette durée du contrat, même si un repos habituel est prévu ce jour-là. Enregistrez uniquement les dates à comptabiliser."
        >
          <div className="wf-input-unit">
            <input
              type="number"
              min={0}
              max={24}
              step={0.5}
              value={absenceHours}
              onChange={(event) => {
                absenceEdited.current = true;
                setAbsenceHours(Number(event.target.value));
              }}
              required
            />
            <span>h / jour</span>
          </div>
        </FormField>
        <fieldset className="wf-fieldset">
          <legend>Indisponibilités récurrentes</legend>
          <p>
            Bloquez une journée ou une plage horaire chaque semaine. Pour une
            absence datée qui réduit les heures dues, utilisez Absences.
          </p>
          <UnavailabilityFields
            rules={unavailability}
            onChange={setUnavailability}
          />
          {!!retained.length && (
            <p>
              {retained.length} autre{retained.length > 1 ? "s" : ""} règle
              {retained.length > 1 ? "s" : ""} déjà configurée
              {retained.length > 1 ? "s" : ""}{" "}
              {retained.length > 1 ? "sont conservées" : "est conservée"}.
            </p>
          )}
        </fieldset>
        <footer>
          <WButton variant="secondary" onClick={onClose}>
            Annuler
          </WButton>
          <WButton type="submit" disabled={saving}>
            {saving
              ? "Enregistrement…"
              : employee
                ? "Enregistrer"
                : "Ajouter la personne"}
          </WButton>
        </footer>
      </form>
    </EditorDialog>
  );
}

function UnavailabilityFields({
  rules,
  onChange,
}: {
  rules: AvailabilityInput[];
  onChange: (rules: AvailabilityInput[]) => void;
}) {
  function update(index: number, patch: Partial<AvailabilityInput>) {
    onChange(
      rules.map((rule, position) =>
        position === index ? { ...rule, ...patch } : rule,
      ),
    );
  }
  return (
    <>
      <div className="wf-availability-list">
        {rules.map((rule, index) => {
          const wholeDay = !rule.startTime && !rule.endTime;
          const nextDay =
            rule.startTime &&
            rule.endTime &&
            time(rule.endTime) < time(rule.startTime);
          return (
            <div className="wf-availability-period" key={index}>
              <div className="wf-availability-fields">
                <FormField label="Jour">
                  <select
                    value={rule.dayInCycle ?? ""}
                    onChange={(event) =>
                      update(index, {
                        dayInCycle:
                          event.target.value === ""
                            ? null
                            : Number(event.target.value),
                      })
                    }
                  >
                    <option value="">Tous les jours</option>
                    {weekdays.map((day, position) => (
                      <option key={day} value={position}>
                        {day}
                      </option>
                    ))}
                  </select>
                </FormField>
                <FormField label="De">
                  <input
                    type="time"
                    step={1800}
                    disabled={wholeDay}
                    required={!wholeDay}
                    value={rule.startTime ? time(rule.startTime) : ""}
                    onChange={(event) =>
                      update(index, { startTime: event.target.value || null })
                    }
                  />
                </FormField>
                <FormField label="À">
                  <input
                    type="time"
                    step={1800}
                    disabled={wholeDay}
                    required={!wholeDay}
                    value={rule.endTime ? time(rule.endTime) : ""}
                    onChange={(event) =>
                      update(index, { endTime: event.target.value || null })
                    }
                  />
                </FormField>
                <button
                  type="button"
                  className="wf-icon-button"
                  aria-label={`Retirer l’indisponibilité ${rule.dayInCycle === null ? "de tous les jours" : `du ${weekdays[rule.dayInCycle]}`}`}
                  onClick={() =>
                    onChange(rules.filter((_, position) => position !== index))
                  }
                >
                  <Icon name="trash" />
                </button>
              </div>
              <div className="wf-availability-caption">
                <label className="wf-checkbox">
                  <input
                    type="checkbox"
                    checked={wholeDay}
                    onChange={(event) =>
                      update(
                        index,
                        event.target.checked
                          ? { startTime: null, endTime: null }
                          : { startTime: "09:00", endTime: "12:00" },
                      )
                    }
                  />
                  <span>Journée entière</span>
                </label>
                {nextDay && <small>Fin le lendemain</small>}
              </div>
            </div>
          );
        })}
      </div>
      {!rules.length && (
        <p className="wf-availability-empty">
          Aucune indisponibilité récurrente. La personne est disponible sur tous
          les créneaux de la période.
        </p>
      )}
      <WButton
        variant="quiet"
        icon="plus"
        onClick={() =>
          onChange([
            ...rules,
            {
              effect: "UNAVAILABLE",
              dayInCycle: 0,
              startTime: "09:00",
              endTime: "12:00",
              priority: 10,
            },
          ])
        }
      >
        Ajouter une indisponibilité
      </WButton>
    </>
  );
}
