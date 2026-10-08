import React from "react";
import { api } from "../../api/client";
import type { BusinessProfile, TimeModel } from "../../api/types";
import { Button } from "../../ui/Button";
import { Field, SelectField } from "../../ui/Field";
import { ErrorBanner } from "../../ui/Feedback";
import { Modal } from "../../ui/Modal";
import { describeError } from "../../hooks/useAsync";

/** Ce que « ne rien choisir » vaut dans une liste déroulante, qui ne sait pas dire `null`. */
const NO_PROFILE = "__ALL__";

const PROFILE_OPTIONS = [
  { value: "SCHOOL", label: "École" },
  { value: "COMPANY", label: "Entreprise" },
  { value: "RESTAURANT", label: "Restauration" },
  { value: NO_PROFILE, label: "Tout le vocabulaire (expert)" },
];

/**
 * Les rythmes que 95 % des cas veulent, sans avoir à raisonner en jours de cycle.
 *
 * <p>Le cycle se compte **en jours et non en semaines** : c'est ce qui autorise les
 * rotations qui ne tombent pas juste — décadaires, cycles de 10 ou 21 jours. Le
 * mode libre reste là pour elles.
 */
const RHYTHMS = [
  { id: "week", label: "Semaine standard (5 jours ouvrés)", cycleDays: 7, workingDays: 5 },
  { id: "week7", label: "Semaine continue (7 jours)", cycleDays: 7, workingDays: 7 },
  { id: "fortnight", label: "Quinzaine (2 semaines, 5 jours ouvrés)", cycleDays: 14, workingDays: 12 },
  { id: "custom", label: "Cycle libre…", cycleDays: 7, workingDays: 5 },
];

type TimeModelChoice = { kind: "existing"; id: string } | { kind: "new" };

type Break = { afterOrdinal: number; minutes: number };

/**
 * Une pause d'un quart d'heure passe, une pause déjeuner non.
 *
 * <p>C'est la valeur du jeu de démonstration, et elle exprime la seule règle qui
 * compte ici : une séance de 2 h à cheval sur la récréation est normale en
 * collège, coupée par le déjeuner elle ne l'est pas.
 */
const GAP_TOLERANCE_WITH_BREAKS = 20;

function toMinutes(time: string): number {
  const [hours, minutes] = time.split(":").map(Number);
  return hours * 60 + minutes;
}

function toClock(total: number): string {
  const hours = Math.floor(total / 60) % 24;
  const minutes = total % 60;
  return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}`;
}

/**
 * La journée telle que le backend la produira.
 *
 * <p>Même algorithme que `ReferentialService.createTimeModel` : le curseur avance
 * de la durée d'un créneau, puis de la pause éventuelle. Le recopier ici est une
 * duplication assumée — elle rend `afterOrdinal` évident, là où un numéro de
 * créneau abstrait se saisit à l'aveugle.
 */
function previewDay(
  dayStart: string,
  slotMinutes: number,
  slotsPerDay: number,
  breaks: Break[]
): { ordinal: number; start: string; end: string; pause: number }[] {
  const byOrdinal = new Map<number, number>();
  breaks.forEach((entry) =>
    byOrdinal.set(entry.afterOrdinal, (byOrdinal.get(entry.afterOrdinal) ?? 0) + entry.minutes)
  );

  let cursor = toMinutes(dayStart);
  const rows = [];
  for (let ordinal = 0; ordinal < slotsPerDay; ordinal++) {
    const end = cursor + slotMinutes;
    const pause = byOrdinal.get(ordinal) ?? 0;
    rows.push({ ordinal, start: toClock(cursor), end: toClock(end), pause });
    cursor = end + pause;
  }
  return rows;
}

/**
 * Création d'un planning — un scénario, et si besoin son modèle temporel.
 *
 * <p>Jusqu'ici le seul moyen d'obtenir un scénario était de **dériver** le
 * précédent, ce qui recopiait son modèle temporel : impossible de démarrer un
 * planning sur un autre rythme sans passer par l'API à la main.
 *
 * <p>La verticale choisie ici ne change **rien au moteur** — aucun plugin ne la
 * lit. Elle choisit le vocabulaire d'attributs que les formulaires proposent, et
 * « Tout le vocabulaire » l'ouvre en grand.
 */
export function ScenarioCreator(props: {
  tenantId: string;
  tenantProfile: BusinessProfile;
  timeModels: TimeModel[];
  onClose: () => void;
  onCreated: (scenarioId: string) => void;
}) {
  const [label, setLabel] = React.useState("");
  const [profile, setProfile] = React.useState<string>(props.tenantProfile);
  const [choice, setChoice] = React.useState<TimeModelChoice>(() =>
    props.timeModels.length > 0
      ? { kind: "existing", id: props.timeModels[0].id }
      : { kind: "new" }
  );

  const [rhythm, setRhythm] = React.useState("week");
  const [cycleDays, setCycleDays] = React.useState(7);
  const [workingDays, setWorkingDays] = React.useState(5);
  const [dayStart, setDayStart] = React.useState("08:00");
  const [slotMinutes, setSlotMinutes] = React.useState(60);
  const [slotsPerDay, setSlotsPerDay] = React.useState(8);
  const [maxGapMinutes, setMaxGapMinutes] = React.useState(0);
  const [breaks, setBreaks] = React.useState<Break[]>([]);

  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();

  function chooseRhythm(id: string) {
    setRhythm(id);
    const preset = RHYTHMS.find((candidate) => candidate.id === id);
    if (preset && id !== "custom") {
      setCycleDays(preset.cycleDays);
      setWorkingDays(preset.workingDays);
    }
  }

  const invalid =
    label.trim() === "" ||
    (choice.kind === "new" && (workingDays > cycleDays || cycleDays < 1 || slotsPerDay < 1));

  async function create() {
    setSaving(true);
    setError(undefined);
    try {
      // Le modèle temporel d'abord : le scénario ne peut pas exister sans lui.
      const timeModelId =
        choice.kind === "existing"
          ? choice.id
          : (
              await api.createTimeModel(props.tenantId, {
                label: `${label.trim()} — rythme`,
                cycleDays,
                workingDays,
                dayStart,
                slotMinutes,
                slotsPerDay,
                maxGapMinutes,
                breaks,
              })
            ).id;

      const scenario = await api.createScenario({
        tenantId: props.tenantId,
        label: label.trim(),
        timeModelId,
        profile: profile === NO_PROFILE ? null : (profile as BusinessProfile),
      });
      props.onCreated(scenario.id);
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title="Nouveau planning"
      description="Un planning porte son propre rythme, ses règles et ses poids. La verticale ne change rien au moteur : elle choisit les champs métier proposés sur les ressources."
      width="lg"
      onClose={props.onClose}
      footer={
        <>
          <Button variant="secondary" onClick={props.onClose}>
            Annuler
          </Button>
          <Button onClick={create} disabled={saving || invalid}>
            {saving ? "Création…" : "Créer"}
          </Button>
        </>
      }
    >
      {error ? <ErrorBanner message={error} /> : null}

      <div className="space-y-5">
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Nom du planning" value={label} onChange={setLabel} placeholder="Rentrée 2027" />
          <SelectField
            label="Verticale"
            value={profile}
            onChange={setProfile}
            options={PROFILE_OPTIONS}
          />
        </div>

        <div>
          <p className="app-muted mb-3 text-xs font-bold uppercase tracking-wide">Rythme</p>

          <SelectField
            label="Modèle temporel"
            value={choice.kind === "existing" ? choice.id : "__NEW__"}
            onChange={(value) =>
              setChoice(value === "__NEW__" ? { kind: "new" } : { kind: "existing", id: value })
            }
            options={[
              ...props.timeModels.map((model) => ({
                value: model.id,
                label: `${model.label} — ${model.cycleDays ?? "?"} jours, ${model.slotCount} créneaux`,
              })),
              { value: "__NEW__", label: "Créer un nouveau rythme…" },
            ]}
          />

          {choice.kind === "new" ? (
            <div className="app-surface-muted mt-3 space-y-3 rounded-lg border p-3">
              <SelectField
                label="Cadence"
                value={rhythm}
                onChange={chooseRhythm}
                options={RHYTHMS.map((preset) => ({ value: preset.id, label: preset.label }))}
              />

              {rhythm === "custom" ? (
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field
                    label="Jours du cycle"
                    type="number"
                    value={String(cycleDays)}
                    onChange={(value) => setCycleDays(Number(value))}
                    hint="Le cycle se compte en jours : 10 ou 21 sont des rotations légitimes."
                  />
                  <Field
                    label="Jours travaillés"
                    type="number"
                    value={String(workingDays)}
                    onChange={(value) => setWorkingDays(Number(value))}
                    hint="Les jours suivants du cycle restent vides."
                  />
                </div>
              ) : null}

              <div className="grid gap-3 sm:grid-cols-2">
                <Field label="Début de journée" type="time" value={dayStart} onChange={setDayStart} />
                <Field
                  label="Créneaux par jour"
                  type="number"
                  value={String(slotsPerDay)}
                  onChange={(value) => setSlotsPerDay(Number(value))}
                />
                <Field
                  label="Durée d'un créneau (min)"
                  type="number"
                  value={String(slotMinutes)}
                  onChange={(value) => setSlotMinutes(Number(value))}
                  hint="Tous les créneaux d'un modèle ont la même durée."
                />
                <Field
                  label="Coupure enjambable (min)"
                  type="number"
                  value={String(maxGapMinutes)}
                  onChange={(value) => setMaxGapMinutes(Number(value))}
                  hint="0 : aucune activité n'enjambe une interruption. C'est un choix métier."
                />
              </div>

              <BreaksBlock
                breaks={breaks}
                slotsPerDay={slotsPerDay}
                onChange={(next) => {
                  setBreaks(next);
                  // Une grille sans pause n'a rien à tolérer ; dès qu'il y en a
                  // une, refuser toute coupure interdirait aussi d'enjamber une
                  // récréation de 15 min, ce qui est rarement voulu.
                  if (next.length > 0 && maxGapMinutes === 0) {
                    setMaxGapMinutes(GAP_TOLERANCE_WITH_BREAKS);
                  }
                }}
              />

              <DayPreview
                rows={previewDay(dayStart, slotMinutes, slotsPerDay, breaks)}
                maxGapMinutes={maxGapMinutes}
              />

              {workingDays > cycleDays ? (
                <p className="text-xs text-red-600">
                  Les jours travaillés ({workingDays}) dépassent la longueur du cycle ({cycleDays}).
                </p>
              ) : null}
            </div>
          ) : null}
        </div>

        <p className="app-muted text-xs">
          L'horizon est cyclique : la grille se répète à l'identique sur toute la
          période. Les absences datées relèvent de l'horizon daté, prévu au jalon 4.
        </p>
      </div>
    </Modal>
  );
}

/**
 * Les pauses de la journée — récréations, coupure déjeuner.
 *
 * <p>Une pause n'est pas un créneau : c'est un **intervalle** entre deux créneaux.
 * La grille exige que tous les créneaux aient la même durée ; ce sont les trous
 * entre eux qui restent libres, et c'est exactement ce qui permet de décrire une
 * pause déjeuner sans sortir du générateur.
 *
 * <p>La même pause s'applique à tous les jours travaillés. Une journée
 * particulière — le mercredi raccourci — se décrit en retirant la disponibilité
 * des ressources concernées, pas en tordant la grille.
 */
function BreaksBlock(props: {
  breaks: Break[];
  slotsPerDay: number;
  onChange: (breaks: Break[]) => void;
}) {
  function update(index: number, patch: Partial<Break>) {
    props.onChange(
      props.breaks.map((entry, position) =>
        position === index ? { ...entry, ...patch } : entry
      )
    );
  }

  return (
    <div>
      <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
        <span className="text-sm font-medium">Pauses</span>
        <Button
          variant="secondary"
          onClick={() =>
            props.onChange([
              ...props.breaks,
              // Par défaut au milieu de la journée : c'est la pause déjeuner,
              // le cas qui amène ici neuf fois sur dix.
              { afterOrdinal: Math.max(Math.floor(props.slotsPerDay / 2) - 1, 0), minutes: 60 },
            ])
          }
        >
          Ajouter une pause
        </Button>
      </div>

      {props.breaks.length === 0 ? (
        <p className="app-muted text-xs">
          Aucune pause : la journée enchaîne les créneaux sans interruption.
        </p>
      ) : (
        <div className="space-y-2">
          {props.breaks.map((entry, index) => (
            <div key={index} className="flex flex-wrap items-end gap-2">
              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">Après le créneau</span>
                <select
                  className="app-input py-1.5 text-sm"
                  value={entry.afterOrdinal}
                  onChange={(event) => update(index, { afterOrdinal: Number(event.target.value) })}
                >
                  {Array.from({ length: Math.max(props.slotsPerDay - 1, 0) }, (_, ordinal) => (
                    <option key={ordinal} value={ordinal}>
                      {ordinal + 1}
                      {ordinal === 0 ? "er" : "e"}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">Durée (min)</span>
                <input
                  className="app-input w-24 py-1.5 text-sm"
                  type="number"
                  min={1}
                  step={5}
                  value={entry.minutes}
                  onChange={(event) => update(index, { minutes: Number(event.target.value) })}
                />
              </label>

              <Button
                variant="ghost"
                onClick={() => props.onChange(props.breaks.filter((_, i) => i !== index))}
              >
                Suppr.
              </Button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

/**
 * La journée résultante, horaires réels à l'appui.
 *
 * <p>« Après le 4e créneau, 75 minutes » ne dit rien à personne ; « 12:15 → 13:30 »
 * se relit d'un coup d'œil. C'est le seul moyen de vérifier une grille avant de
 * la créer — une fois le modèle temporel enregistré, la corriger demande de le
 * refaire.
 */
function DayPreview(props: {
  rows: { ordinal: number; start: string; end: string; pause: number }[];
  maxGapMinutes: number;
}) {
  const last = props.rows[props.rows.length - 1];

  return (
    <div>
      <p className="app-muted mb-1 text-xs font-medium">
        Journée type — fin à {last ? last.end : "—"}
      </p>
      <div className="app-surface flex flex-wrap gap-1 rounded-lg border p-2 text-xs">
        {props.rows.map((row) => (
          <React.Fragment key={row.ordinal}>
            <span className="app-chip">
              {row.start} – {row.end}
            </span>
            {row.pause > 0 ? (
              <span
                className="app-chip"
                style={
                  row.pause <= props.maxGapMinutes
                    ? undefined
                    : { borderColor: "var(--app-accent)", color: "var(--app-accent)" }
                }
                title={
                  row.pause <= props.maxGapMinutes
                    ? "Une séance longue peut enjamber cette pause."
                    : "Aucune séance ne peut enjamber cette pause."
                }
              >
                ⏸ {row.pause} min
              </span>
            ) : null}
          </React.Fragment>
        ))}
      </div>
      <p className="app-muted mt-1 text-xs">
        Les pauses en couleur sont infranchissables ; les autres tolèrent qu'une
        séance longue les enjambe, dans la limite de {props.maxGapMinutes} min.
      </p>
    </div>
  );
}
