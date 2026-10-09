import React from "react";
import { workforceApi } from "../../api/workforce";
import type {
  AbsenceReason,
  Employee,
  Shift,
  ShiftRole,
  WorkforceOverview,
  WorkforceProfile,
} from "./types";
import { addDays, dateLabel, humanError, time } from "./format";
import {
  EditorDialog,
  ErrorNotice,
  FormField,
  Icon,
  WButton,
} from "./components";

type EditorProps = {
  overview: WorkforceOverview;
  onClose: () => void;
  onSaved: () => Promise<void>;
};
const weekdays = [
  "Lundi",
  "Mardi",
  "Mercredi",
  "Jeudi",
  "Vendredi",
  "Samedi",
  "Dimanche",
];

export function EmployeeEditor({
  overview,
  employee,
  onClose,
  onSaved,
}: EditorProps & { employee?: Employee }) {
  const [label, setLabel] = React.useState(employee?.label ?? "");
  const [skills, setSkills] = React.useState(employee?.skills.join(", ") ?? "");
  const [hours, setHours] = React.useState(
    (employee?.contractWeeklyMinutes ?? 2100) / 60,
  );
  const [daysOff, setDaysOff] = React.useState<number[]>(() =>
    (employee?.availability ?? [])
      .filter(
        (rule) =>
          rule.effect === "UNAVAILABLE" &&
          rule.dayInCycle !== null &&
          !rule.startTime &&
          !rule.endTime,
      )
      .map((rule) => rule.dayInCycle as number),
  );
  const [saving, setSaving] = React.useState(false);
  const savedId = React.useRef(employee?.id);
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
    setSaving(true);
    try {
      const retained = (employee?.availability ?? [])
        .filter(
          (rule) =>
            !(
              rule.effect === "UNAVAILABLE" &&
              rule.dayInCycle !== null &&
              !rule.startTime &&
              !rule.endTime
            ),
        )
        .map(({ id: _id, ...rule }) => rule);
      const saved = await workforceApi.saveEmployee(
        overview.scenario.id,
        {
          label: label.trim(),
          skills: parsedSkills,
          contractWeeklyMinutes: Math.round(hours * 60),
          availability: [
            ...retained,
            ...daysOff.map((day) => ({
              effect: "UNAVAILABLE" as const,
              dayInCycle: day,
              startTime: null,
              endTime: null,
              priority: 10,
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
      description="Ses compétences déterminent les postes qui peuvent lui être attribués."
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
        <FormField
          label="Heures au contrat par semaine"
          hint="Ce volume est une limite hebdomadaire et un objectif de répartition. 0 signifie qu’aucune heure ne doit être attribuée."
        >
          <div className="wf-input-unit">
            <input
              type="number"
              min={0}
              max={168}
              step={0.5}
              value={hours}
              onChange={(event) => setHours(Number(event.target.value))}
              required
            />
            <span>h / semaine</span>
          </div>
        </FormField>
        <fieldset className="wf-fieldset">
          <legend>Jours habituellement indisponibles</legend>
          <p>
            Laissez tout décoché pour une personne disponible chaque jour. Les
            congés se renseignent dans Absences.
          </p>
          <div className="wf-day-checks">
            {weekdays.map((day, index) => (
              <label key={day}>
                <input
                  type="checkbox"
                  checked={daysOff.includes(index)}
                  onChange={(event) =>
                    setDaysOff((current) =>
                      event.target.checked
                        ? [...current, index]
                        : current.filter((value) => value !== index),
                    )
                  }
                />
                <span>{day.slice(0, 3)}</span>
              </label>
            ))}
          </div>
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

export function ShiftEditor({
  overview,
  shift,
  selectedDate,
  onClose,
  onSaved,
}: EditorProps & { shift?: Shift; selectedDate?: string }) {
  const restaurant = overview.tenant.profile === "RESTAURANT";
  const [label, setLabel] = React.useState(
    shift?.label ?? (restaurant ? "Service du midi" : "Équipe du matin"),
  );
  const [date, setDate] = React.useState(
    shift?.date ?? selectedDate ?? overview.period.from,
  );
  const [startTime, setStartTime] = React.useState(
    time(shift?.startTime ?? (restaurant ? "11:30" : "09:00")),
  );
  const [endTime, setEndTime] = React.useState(
    time(shift?.endTime ?? (restaurant ? "15:00" : "17:00")),
  );
  const [nextDay, setNextDay] = React.useState(shift?.nextDay ?? false);
  const [roles, setRoles] = React.useState<ShiftRole[]>(
    shift?.roles.map((role) => ({ ...role })) ?? [
      { role: restaurant ? "Cuisine" : "Équipe", quantity: 1, skill: "" },
    ],
  );
  const savedId = React.useRef(shift?.id);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();
  const skills = [
    ...new Set(overview.employees.flatMap((employee) => employee.skills)),
  ];
  const suggestionsId = React.useId();
  function updateRole(index: number, patch: Partial<ShiftRole>) {
    setRoles((current) =>
      current.map((role, position) =>
        position === index ? { ...role, ...patch } : role,
      ),
    );
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setError(undefined);
    if (!nextDay && endTime <= startTime) {
      setError(
        "L’heure de fin doit suivre l’heure de début. Pour un service de nuit, cochez « Fin le lendemain ».",
      );
      return;
    }
    if (nextDay && addDays(date, 1) > overview.period.to) {
      setError(
        "La fin du service dépasse la période de cet espace. Choisissez une date antérieure.",
      );
      return;
    }
    setSaving(true);
    try {
      const saved = await workforceApi.saveShift(
        overview.scenario.id,
        {
          label: label.trim(),
          date,
          startTime,
          endTime,
          nextDay,
          roles: roles.map((role) => ({
            role: role.role.trim(),
            quantity: Number(role.quantity),
            ...(role.skill?.trim() ? { skill: role.skill.trim() } : {}),
          })),
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
      title={
        shift
          ? `Modifier ${shift.label}`
          : restaurant
            ? "Ajouter un service"
            : "Ajouter une plage de travail"
      }
      description="Définissez les horaires et les personnes nécessaires. Le planning attribuera les postes."
      onClose={onClose}
    >
      <form onSubmit={save} className="wf-form">
        {error && <ErrorNotice message={error} />}
        <FormField label={restaurant ? "Nom du service" : "Nom de la plage"}>
          <input
            autoFocus
            value={label}
            onChange={(event) => setLabel(event.target.value)}
            required
            maxLength={120}
          />
        </FormField>
        <FormField label="Date">
          <input
            type="date"
            min={overview.period.from}
            max={overview.period.to}
            value={date}
            onChange={(event) => setDate(event.target.value)}
            required
          />
        </FormField>
        <div className="wf-form-row">
          <FormField label="Début">
            <input
              type="time"
              step={1800}
              value={startTime}
              onChange={(event) => setStartTime(event.target.value)}
              required
            />
          </FormField>
          <FormField label="Fin">
            <input
              type="time"
              step={1800}
              value={endTime}
              onChange={(event) => setEndTime(event.target.value)}
              required
            />
          </FormField>
        </div>
        <label className="wf-checkbox">
          <input
            type="checkbox"
            checked={nextDay}
            onChange={(event) => setNextDay(event.target.checked)}
          />
          <span>Fin le lendemain</span>
        </label>
        <fieldset className="wf-fieldset">
          <legend>Besoins de personnel</legend>
          <p>Une compétence vide autorise toute personne de votre équipe.</p>
          <datalist id={suggestionsId}>
            {skills.map((skill) => (
              <option key={skill} value={skill} />
            ))}
          </datalist>
          {roles.map((role, index) => (
            <div className="wf-role-fields" key={index}>
              <FormField label="Poste">
                <input
                  value={role.role}
                  onChange={(event) =>
                    updateRole(index, { role: event.target.value })
                  }
                  required
                  placeholder={restaurant ? "Salle" : "Accueil"}
                />
              </FormField>
              <FormField label="Personnes">
                <input
                  type="number"
                  min={1}
                  max={100}
                  value={role.quantity}
                  onChange={(event) =>
                    updateRole(index, { quantity: Number(event.target.value) })
                  }
                  required
                />
              </FormField>
              <FormField label="Compétence requise">
                <input
                  list={suggestionsId}
                  value={role.skill ?? ""}
                  onChange={(event) =>
                    updateRole(index, { skill: event.target.value })
                  }
                  placeholder="Toutes"
                />
              </FormField>
              <button
                type="button"
                className="wf-icon-button"
                aria-label={`Retirer le poste ${role.role || index + 1}`}
                disabled={roles.length === 1}
                onClick={() =>
                  setRoles((current) =>
                    current.filter((_, position) => position !== index),
                  )
                }
              >
                <Icon name="trash" />
              </button>
            </div>
          ))}
          <WButton
            variant="quiet"
            icon="plus"
            onClick={() =>
              setRoles((current) => [
                ...current,
                { role: "", quantity: 1, skill: "" },
              ])
            }
          >
            Ajouter un poste
          </WButton>
        </fieldset>
        <footer>
          <WButton variant="secondary" onClick={onClose}>
            Annuler
          </WButton>
          <WButton type="submit" disabled={saving}>
            {saving
              ? "Enregistrement…"
              : shift
                ? "Enregistrer"
                : restaurant
                  ? "Ajouter le service"
                  : "Ajouter la plage"}
          </WButton>
        </footer>
      </form>
    </EditorDialog>
  );
}

export function AbsenceEditor({ overview, onClose, onSaved }: EditorProps) {
  const [resourceId, setResourceId] = React.useState(
    overview.employees[0]?.id ?? "",
  );
  const [from, setFrom] = React.useState(overview.period.from);
  const [to, setTo] = React.useState(overview.period.from);
  const [reason, setReason] = React.useState<AbsenceReason>("LEAVE");
  const [note, setNote] = React.useState("");
  const saved = React.useRef(false);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(undefined);
    try {
      if (!saved.current) {
        await workforceApi.createAbsence(overview.scenario.id, {
          resourceId,
          from,
          to,
          reason,
          note: note.trim() || null,
        });
        saved.current = true;
      }
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
      title="Ajouter une absence"
      description="La personne ne pourra être affectée sur ces journées. Recalculez le planning après l’enregistrement."
      onClose={onClose}
    >
      <form onSubmit={save} className="wf-form">
        {error && <ErrorNotice message={error} />}
        <FormField label="Personne">
          <select
            value={resourceId}
            onChange={(event) => setResourceId(event.target.value)}
            required
            autoFocus
          >
            {overview.employees.map((employee) => (
              <option key={employee.id} value={employee.id}>
                {employee.label}
              </option>
            ))}
          </select>
        </FormField>
        <div className="wf-form-row">
          <FormField label="Premier jour">
            <input
              type="date"
              value={from}
              min={overview.period.from}
              max={overview.period.to}
              onChange={(event) => {
                setFrom(event.target.value);
                if (to < event.target.value) setTo(event.target.value);
              }}
              required
            />
          </FormField>
          <FormField label="Dernier jour inclus">
            <input
              type="date"
              value={to}
              min={from}
              max={overview.period.to}
              onChange={(event) => setTo(event.target.value)}
              required
            />
          </FormField>
        </div>
        <FormField label="Motif">
          <select
            value={reason}
            onChange={(event) => setReason(event.target.value as AbsenceReason)}
          >
            <option value="LEAVE">Congé</option>
            <option value="SICKNESS">Maladie</option>
            <option value="OTHER">Autre absence</option>
          </select>
        </FormField>
        <FormField label="Note facultative">
          <textarea
            rows={3}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            maxLength={500}
            placeholder="Précision utile pour l’organisation"
          />
        </FormField>
        <footer>
          <WButton variant="secondary" onClick={onClose}>
            Annuler
          </WButton>
          <WButton type="submit" disabled={saving || !resourceId}>
            {saving ? "Enregistrement…" : "Ajouter l’absence"}
          </WButton>
        </footer>
      </form>
    </EditorDialog>
  );
}

export function WorkplaceEditor({
  profile,
  onClose,
  onCreated,
}: {
  profile: WorkforceProfile;
  onClose: () => void;
  onCreated: (scenarioId: string) => Promise<void>;
}) {
  const restaurant = profile === "RESTAURANT";
  const today = new Date();
  const todayDate = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, "0")}-${String(today.getDate()).padStart(2, "0")}`;
  const [label, setLabel] = React.useState("");
  const [from, setFrom] = React.useState(
    addDays(todayDate, -((today.getDay() + 6) % 7)),
  );
  const [days, setDays] = React.useState(7);
  const [demo, setDemo] = React.useState(false);
  const savedId = React.useRef<string>();
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();
  async function save(event: React.FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(undefined);
    try {
      if (!savedId.current) {
        const created = await workforceApi.createWorkspace({
          label: label.trim(),
          profile,
          from,
          to: addDays(from, days - 1),
          demo,
        });
        savedId.current = created.scenarioId;
      }
      await onCreated(savedId.current);
      onClose();
    } catch (cause) {
      setError(humanError(cause));
    } finally {
      setSaving(false);
    }
  }
  return (
    <EditorDialog
      title={restaurant ? "Votre espace restaurant" : "Votre espace entreprise"}
      description="Une période, une équipe et les besoins de travail à organiser."
      onClose={onClose}
    >
      <form onSubmit={save} className="wf-form">
        {error && <ErrorNotice message={error} />}
        <FormField
          label={
            restaurant
              ? "Nom du restaurant"
              : "Nom de l’entreprise ou de l’équipe"
          }
        >
          <input
            autoFocus
            required
            maxLength={120}
            placeholder={
              restaurant
                ? "Le nom de votre établissement"
                : "Le nom de votre organisation"
            }
            value={label}
            onChange={(event) => setLabel(event.target.value)}
          />
        </FormField>
        <div className="wf-form-row">
          <FormField label="Premier jour">
            <input
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
              required
            />
          </FormField>
          <FormField label="Période">
            <select
              value={days}
              onChange={(event) => setDays(Number(event.target.value))}
            >
              <option value={7}>7 jours</option>
              <option value={14}>14 jours</option>
              <option value={28}>28 jours</option>
            </select>
          </FormField>
        </div>
        <p className="wf-form-summary">
          Du {dateLabel(from)} au {dateLabel(addDays(from, days - 1))}.
        </p>
        <div className="wf-start-options">
          <label className={!demo ? "selected" : ""}>
            <input
              type="radio"
              name="workspace-mode"
              checked={!demo}
              onChange={() => setDemo(false)}
            />
            <strong>Commencer avec mon équipe</strong>
            <span>
              Un espace vide à compléter avec vos personnes et vos besoins.
            </span>
          </label>
          <label className={demo ? "selected" : ""}>
            <input
              type="radio"
              name="workspace-mode"
              checked={demo}
              onChange={() => setDemo(true)}
            />
            <strong>Explorer une démonstration</strong>
            <span>
              Une équipe et des {restaurant ? "services" : "horaires"}{" "}
              d’exemple, clairement identifiés et modifiables.
            </span>
          </label>
        </div>
        <footer>
          <WButton variant="secondary" onClick={onClose}>
            Annuler
          </WButton>
          <WButton type="submit" disabled={saving}>
            {saving
              ? "Création…"
              : demo
                ? "Créer la démonstration"
                : "Créer mon espace"}
          </WButton>
        </footer>
      </form>
    </EditorDialog>
  );
}

export function DeleteConfirmation({
  title,
  detail,
  onClose,
  onDelete,
}: {
  title: string;
  detail: string;
  onClose: () => void;
  onDelete: () => Promise<void>;
}) {
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string>();
  async function remove() {
    setBusy(true);
    try {
      await onDelete();
      onClose();
    } catch (cause) {
      setError(humanError(cause));
    } finally {
      setBusy(false);
    }
  }
  return (
    <EditorDialog title={title} description={detail} onClose={onClose}>
      <div className="wf-form">
        {error && <ErrorNotice message={error} />}
        <footer>
          <WButton variant="secondary" onClick={onClose}>
            Conserver
          </WButton>
          <WButton variant="danger" disabled={busy} onClick={remove}>
            {busy ? "Suppression…" : "Supprimer"}
          </WButton>
        </footer>
      </div>
    </EditorDialog>
  );
}
