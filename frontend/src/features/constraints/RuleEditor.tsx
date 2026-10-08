import React from "react";
import { api } from "../../api/client";
import type { ConstraintConfig, Demand, Plugin, Resource, ResourceType } from "../../api/types";
import { Button } from "../../ui/Button";
import { ErrorBanner } from "../../ui/Feedback";
import { Modal } from "../../ui/Modal";
import { describeError } from "../../hooks/useAsync";
import { AdvancedJson, PRIORITIES, priorityLabel } from "./ruleControls";
import { ruleSentence } from "./ruleSentence";
import { SchemaForm } from "./SchemaForm";
import { ScopeEditor } from "./ScopeEditor";

/** Les valeurs par défaut d'un nouveau réglage, déduites du schéma du plugin. */
function defaultParams(plugin: Plugin): Record<string, unknown> {
  const properties = plugin.paramSchema?.properties ?? {};
  const params: Record<string, unknown> = {};
  Object.entries(properties).forEach(([key, property]) => {
    if (property.enum) params[key] = property.enum[0];
    else if (property["x-unit"] === "timeOfDay") params[key] = "08:00";
    else if (property.type === "integer" || property.type === "number") {
      params[key] = property["x-unit"] === "minutes" ? 60 : (property.minimum ?? 1);
    } else params[key] = "";
  });
  return params;
}

export function RuleEditor(props: {
  scenarioId: string;
  plugin: Plugin;
  config?: ConstraintConfig;
  resourceTypes: ResourceType[];
  resources: Resource[];
  demands: Demand[];
  onClose: () => void;
  onSaved: () => void;
}) {
  const [scope, setScope] = React.useState<unknown>(props.config?.scope ?? {});
  const [params, setParams] = React.useState<Record<string, unknown>>(
    props.config?.params ?? defaultParams(props.plugin)
  );
  const [weight, setWeight] = React.useState(props.config?.weight ?? 1);
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();

  const { population, settings } = ruleSentence(props.plugin, scope, params, props.resourceTypes);

  async function save() {
    setSaving(true);
    setError(undefined);
    try {
      const payload = { type: props.plugin.type, scope, params, weight, enabled: true };
      if (props.config) await api.updateConstraintConfig(props.config.id, payload);
      else await api.createConstraintConfig(props.scenarioId, payload);
      props.onSaved();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal
      title={props.config ? "Modifier la règle" : `Nouvelle règle — ${props.plugin.label}`}
      description={props.plugin.description}
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
      {/* La phrase se met à jour à chaque frappe : c'est elle qu'on relit, pas le JSON. */}
      <div
        className="mb-4 rounded-lg border p-3 text-sm"
        style={{ borderColor: "var(--app-accent-soft)" }}
      >
        <strong>{population}</strong>
        {settings ? <span className="app-muted"> — {settings}</span> : null}
      </div>

      {error ? <ErrorBanner message={error} /> : null}

      <div className="space-y-5">
        <ScopeEditor
          scenarioId={props.scenarioId}
          target={props.plugin.scopeTarget}
          resourceTypes={props.resourceTypes}
          resources={props.resources}
          demands={props.demands}
          value={scope}
          onChange={setScope}
        />

        <div>
          <p className="app-muted mb-3 text-xs font-bold uppercase tracking-wide">Réglages</p>
          <SchemaForm schema={props.plugin.paramSchema} value={params} onChange={setParams} />
        </div>

        <div>
          <p className="app-muted mb-2 text-xs font-bold uppercase tracking-wide">
            Priorité de cette règle
          </p>
          <div className="app-surface-muted inline-flex rounded-lg border p-0.5">
            {PRIORITIES.map((priority) => (
              <button
                key={priority.value}
                type="button"
                onClick={() => setWeight(priority.value)}
                className="rounded-md px-3 py-1.5 text-sm font-semibold transition"
                style={
                  weight === priority.value
                    ? { background: "var(--app-accent)", color: "white" }
                    : undefined
                }
              >
                {priority.label}
              </button>
            ))}
          </div>
          <p className="app-muted mt-1.5 text-xs">
            {priorityLabel(weight)} — départage les règles de cette même famille entre
            elles. Le caractère obligatoire ou souhaitable, lui, se règle sur la famille.
          </p>
        </div>

        <AdvancedJson
          scope={scope}
          params={params}
          onScope={setScope}
          onParams={(value) => setParams(value as Record<string, unknown>)}
        />
      </div>
    </Modal>
  );
}
