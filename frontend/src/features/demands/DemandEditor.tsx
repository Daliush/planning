import React from "react";
import { api } from "../../api/client";
import type { Demand, Requirement, ResourceType } from "../../api/types";
import { Button } from "../../ui/Button";
import { Field, JsonField, SelectField } from "../../ui/Field";
import { ErrorBanner } from "../../ui/Feedback";
import { Modal } from "../../ui/Modal";
import { describeError } from "../../hooks/useAsync";
import { parseJson, prettyJson } from "../../utils/format";

export function DemandEditor(props: {
  scenarioId: string;
  demand?: Demand;
  resourceTypes: ResourceType[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [label, setLabel] = React.useState(props.demand?.label ?? "");
  const [duration, setDuration] = React.useState(String(props.demand?.durationMinutes ?? 60));
  const [occurrences, setOccurrences] = React.useState(String(props.demand?.occurrences ?? 1));
  const [attributes, setAttributes] = React.useState(prettyJson(props.demand?.attributes));
  const [placement, setPlacement] = React.useState(
    prettyJson(props.demand?.placement ?? { strategy: "free" })
  );
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();

  async function save() {
    setSaving(true);
    setError(undefined);
    try {
      const payload = {
        label,
        durationMinutes: Number(duration),
        occurrences: Number(occurrences),
        attributes: parseJson(attributes),
        placement: parseJson(placement),
      };
      if (props.demand) await api.updateDemand(props.demand.id, payload);
      else await api.createDemand(props.scenarioId, payload);
      props.onSaved();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={props.demand ? `Modifier « ${props.demand.label} »` : "Nouvelle demande"}
      description="Une demande crée des cases à remplir. Une contrainte, elle, juge une affectation qui existe déjà."
      width="lg"
      onClose={props.onClose}
      footer={
        <>
          <Button variant="secondary" onClick={props.onClose}>
            Fermer
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
          <p className="app-muted mb-3 text-xs font-bold uppercase tracking-wide">
            Ce qu'il faut planifier
          </p>
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Libellé" value={label} onChange={setLabel} className="sm:col-span-3" />
            <Field
              label="Durée (minutes)"
              type="number"
              value={duration}
              onChange={setDuration}
              hint="Jamais en créneaux : la grille est un paramètre modifiable."
            />
            <Field
              label="Occurrences par cycle"
              type="number"
              value={occurrences}
              onChange={setOccurrences}
            />
          </div>
          <div className="mt-3 grid gap-3 sm:grid-cols-2">
            <JsonField
              label="Attributs"
              value={attributes}
              onChange={setAttributes}
              rows={5}
              hint="Lus par les scopes de règles — matière, niveau, groupe."
            />
            <JsonField
              label="Placement"
              value={placement}
              onChange={setPlacement}
              rows={5}
              hint={'free · {"strategy":"window","from":"08:00","to":"12:00"} · {"strategy":"fixed","startTime":"07:00","dayInCycle":0}'}
            />
          </div>
        </div>

        {props.demand ? (
          <RequirementsBlock demand={props.demand} resourceTypes={props.resourceTypes} />
        ) : (
          <p className="app-muted text-sm">
            Enregistrez la demande pour lui ajouter des postes à pourvoir.
          </p>
        )}
      </div>
    </Modal>
  );
}

/**
 * Les postes s'ajoutent et se retirent à l'unité, chacun par son propre appel.
 * L'état local évite de recharger toute la page — et donc de fermer la modale —
 * à chaque ajout.
 */
function RequirementsBlock(props: { demand: Demand; resourceTypes: ResourceType[] }) {
  const [requirements, setRequirements] = React.useState<Requirement[]>(props.demand.requirements);
  const [role, setRole] = React.useState("");
  const [typeId, setTypeId] = React.useState(props.resourceTypes[0]?.id ?? "");
  const [quantity, setQuantity] = React.useState("1");
  const [criteria, setCriteria] = React.useState("{}");
  const [error, setError] = React.useState<string>();

  async function add() {
    setError(undefined);
    try {
      const created = await api.addRequirement(props.demand.id, {
        role,
        typeId,
        quantity: Number(quantity),
        criteria: parseJson(criteria),
      });
      setRequirements((current) => [...current, created]);
      setRole("");
      setCriteria("{}");
    } catch (cause) {
      setError(describeError(cause));
    }
  }

  async function remove(requirementId: string) {
    setError(undefined);
    try {
      await api.deleteRequirement(requirementId);
      setRequirements((current) => current.filter((item) => item.id !== requirementId));
    } catch (cause) {
      setError(describeError(cause));
    }
  }

  return (
    <div>
      <p className="app-muted mb-1 text-xs font-bold uppercase tracking-wide">Postes à pourvoir</p>
      <p className="app-muted mb-3 text-xs">
        Le rôle dit quelle fonction est occupée, le type dit ce qu'est la ressource. Ils
        coïncident souvent, pas toujours.
      </p>

      {error ? <ErrorBanner message={error} /> : null}

      <ul className="mb-4 space-y-1">
        {requirements.map((requirement) => (
          <li
            key={requirement.id}
            className="app-surface-muted flex items-center justify-between gap-2 rounded-lg border p-2 text-sm"
          >
            <span className="min-w-0 truncate">
              {requirement.quantity} × <strong>{requirement.role}</strong> ({requirement.typeCode})
              <span className="app-muted ml-2 font-mono text-xs">
                {JSON.stringify(requirement.criteria)}
              </span>
            </span>
            <Button variant="ghost" onClick={() => remove(requirement.id)}>
              Suppr.
            </Button>
          </li>
        ))}
        {requirements.length === 0 ? (
          <li className="text-sm text-red-600">Aucun poste : rien ne sera affecté à cette demande.</li>
        ) : null}
      </ul>

      <div className="grid items-end gap-3 sm:grid-cols-4">
        <Field label="Rôle" value={role} onChange={setRole} placeholder="enseignant" />
        <SelectField
          label="Type accepté"
          value={typeId}
          onChange={setTypeId}
          options={props.resourceTypes.map((type) => ({ value: type.id, label: type.label }))}
        />
        <Field label="Quantité" type="number" value={quantity} onChange={setQuantity} />
        <Button onClick={add} disabled={!role || !typeId}>
          Ajouter le poste
        </Button>
        <div className="sm:col-span-4">
          <JsonField
            label="Critères d'éligibilité"
            value={criteria}
            onChange={setCriteria}
            rows={3}
            hint={'Ce qui est structurellement impossible. Ex. {"gte":{"path":"/capacite","value":24}}'}
          />
        </div>
      </div>
    </div>
  );
}
