import React from "react";
import { api } from "../../api/client";
import type {
  AttributeDefinition,
  Availability,
  Effect,
  Resource,
  ResourceType,
} from "../../api/types";
import { Button } from "../../ui/Button";
import { Field, SelectField } from "../../ui/Field";
import { ErrorBanner } from "../../ui/Feedback";
import { Modal } from "../../ui/Modal";
import { describeError } from "../../hooks/useAsync";
import { cycleDayOptions, shortTime } from "../../utils/format";
import { AttributesForm } from "./AttributesForm";
import { EFFECT_LABELS } from "./effects";

type Draft = Omit<Availability, "id">;

/**
 * Identité, attributs et disponibilités d'une ressource, dans une seule modale.
 *
 * <p>Les deux relèvent d'endpoints distincts — les disponibilités se remplacent en
 * bloc, parce qu'elles forment un jeu de règles dont la résolution dépend des
 * priorités relatives (§6.5). Mais l'utilisateur, lui, modifie « une ressource » :
 * l'enregistrement enchaîne les deux appels et dit lequel a échoué le cas échéant.
 */
export function ResourceEditor(props: {
  scenarioId: string;
  resource?: Resource;
  resourceTypes: ResourceType[];
  /** Le vocabulaire métier proposable, déjà filtré par la verticale du scénario. */
  attributeDefinitions: AttributeDefinition[];
  /** La longueur du cycle, qui nomme les jours des règles de disponibilité. */
  cycleDays: number;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [label, setLabel] = React.useState(props.resource?.label ?? "");
  const [typeId, setTypeId] = React.useState(
    props.resource?.typeId ?? props.resourceTypes[0]?.id ?? ""
  );
  const [defaultEffect, setDefaultEffect] = React.useState<string>(
    props.resource?.defaultEffect ?? "AVAILABLE"
  );
  const [attributes, setAttributes] = React.useState<Record<string, unknown>>(() =>
    stripType(props.resource?.attributes)
  );
  const [rules, setRules] = React.useState<Draft[]>(
    () => (props.resource?.availability ?? []).map(({ id: _id, ...rest }) => rest)
  );
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();

  const selectedType = props.resourceTypes.find((type) => type.id === typeId);

  async function save() {
    setSaving(true);
    setError(undefined);
    try {
      const payload = { typeId, label, defaultEffect, attributes };
      const saved = props.resource
        ? await api.updateResource(props.resource.id, payload)
        : await api.createResource(props.scenarioId, payload);

      try {
        await api.replaceAvailability(saved.id, rules);
      } catch (cause) {
        setError(`Ressource enregistrée, mais ses disponibilités ont été refusées : ${describeError(cause)}`);
        setSaving(false);
        return;
      }
      props.onSaved();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={props.resource ? `Modifier « ${props.resource.label} »` : "Nouvelle ressource"}
      description="La posture par défaut décrit l'archétype : « indisponible sauf » pour un vacataire, « disponible sauf » pour une salle."
      width="lg"
      onClose={props.onClose}
      footer={
        <>
          <Button variant="secondary" onClick={props.onClose}>
            Annuler
          </Button>
          <Button onClick={save} disabled={saving}>
            {saving ? "Enregistrement…" : "Enregistrer"}
          </Button>
        </>
      }
    >
      {error ? <ErrorBanner message={error} /> : null}

      <div className="space-y-5">
        <div>
          <p className="app-muted mb-3 text-xs font-bold uppercase tracking-wide">Identité</p>
          <div className="grid gap-3 sm:grid-cols-2">
            <Field label="Libellé" value={label} onChange={setLabel} placeholder="Jean Dupont" />
            <SelectField
              label="Type"
              value={typeId}
              onChange={setTypeId}
              options={props.resourceTypes.map((type) => ({ value: type.id, label: type.label }))}
            />
            <SelectField
              label="Posture par défaut"
              value={defaultEffect}
              onChange={setDefaultEffect}
              options={(Object.keys(EFFECT_LABELS) as Effect[]).map((effect) => ({
                value: effect,
                label: EFFECT_LABELS[effect],
              }))}
            />
          </div>
        </div>

        <div>
          <p className="app-muted mb-1 text-xs font-bold uppercase tracking-wide">Attributs métier</p>
          <p className="app-muted mb-3 text-xs">
            Lus par les critères d'éligibilité et les scopes de règles. La clé
            « type » est ajoutée automatiquement.
          </p>
          <AttributesForm
            definitions={props.attributeDefinitions}
            schema={selectedType?.attrSchema}
            resourceTypeCode={selectedType?.code}
            value={attributes}
            onChange={setAttributes}
          />
        </div>

        <AvailabilityBlock rules={rules} onChange={setRules} cycleDays={props.cycleDays} />
      </div>
    </Modal>
  );
}

function AvailabilityBlock(props: {
  rules: Draft[];
  onChange: (rules: Draft[]) => void;
  cycleDays: number;
}) {
  const dayOptions = React.useMemo(() => cycleDayOptions(props.cycleDays), [props.cycleDays]);

  function update(index: number, patch: Partial<Draft>) {
    props.onChange(
      props.rules.map((rule, position) => (position === index ? { ...rule, ...patch } : rule))
    );
  }

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <p className="app-muted text-xs font-bold uppercase tracking-wide">Disponibilités</p>
        <Button
          variant="secondary"
          onClick={() =>
            props.onChange([
              ...props.rules,
              { effect: "UNAVAILABLE", dayInCycle: null, startTime: null, endTime: null, priority: 0 },
            ])
          }
        >
          Ajouter une règle
        </Button>
      </div>

      <p className="app-muted mb-2 text-xs">
        Un champ vide signifie « pas de restriction sur cet axe ». À priorité égale,
        l'indisponibilité l'emporte. Les jours sont ceux du cycle
        {props.cycleDays > 7 ? ` de ${props.cycleDays} jours` : ""}, répétés à
        l'identique sur tout l'horizon — pas des dates du calendrier.
      </p>

      {props.rules.length === 0 ? (
        <p className="app-muted text-sm">
          Aucune règle : la posture par défaut s'applique à tout l'horizon.
        </p>
      ) : (
        <div className="space-y-2">
          {props.rules.map((rule, index) => (
            <div
              key={index}
              className="app-surface-muted grid items-end gap-2 rounded-lg border p-3 sm:grid-cols-5"
            >
              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">Effet</span>
                <select
                  className="app-input py-1.5 text-sm"
                  value={rule.effect}
                  onChange={(event) => update(index, { effect: event.target.value as Effect })}
                >
                  {(Object.keys(EFFECT_LABELS) as Effect[]).map((effect) => (
                    <option key={effect} value={effect}>
                      {EFFECT_LABELS[effect]}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">Jour du cycle</span>
                <select
                  className="app-input py-1.5 text-sm"
                  value={rule.dayInCycle ?? ""}
                  onChange={(event) =>
                    update(index, {
                      dayInCycle: event.target.value === "" ? null : Number(event.target.value),
                    })
                  }
                >
                  <option value="">Tous les jours</option>
                  {dayOptions.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </label>

              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">De</span>
                <input
                  className="app-input py-1.5 text-sm"
                  type="time"
                  value={rule.startTime ? shortTime(rule.startTime) : ""}
                  onChange={(event) => update(index, { startTime: event.target.value || null })}
                />
              </label>

              <label className="block text-xs">
                <span className="app-muted mb-1 block font-medium">À</span>
                <input
                  className="app-input py-1.5 text-sm"
                  type="time"
                  value={rule.endTime ? shortTime(rule.endTime) : ""}
                  onChange={(event) => update(index, { endTime: event.target.value || null })}
                />
              </label>

              <div className="flex items-end gap-2">
                <label className="block flex-1 text-xs">
                  <span className="app-muted mb-1 block font-medium">Priorité</span>
                  <input
                    className="app-input py-1.5 text-sm"
                    type="number"
                    value={rule.priority}
                    onChange={(event) => update(index, { priority: Number(event.target.value) })}
                  />
                </label>
                <Button
                  variant="ghost"
                  onClick={() => props.onChange(props.rules.filter((_, i) => i !== index))}
                >
                  Suppr.
                </Button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function stripType(attributes: Record<string, unknown> | undefined) {
  if (!attributes) return {};
  const { type: _ignored, ...rest } = attributes;
  return rest;
}
