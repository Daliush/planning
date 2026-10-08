import React from "react";
import { api } from "../../api/client";
import type {
  ConstraintConfig,
  ConstraintWeight,
  Level,
  Plugin,
  ResourceType,
} from "../../api/types";
import { Button } from "../../ui/Button";
import { ErrorBanner, Loading } from "../../ui/Feedback";
import { Card, Chip, Section } from "../../ui/Section";
import { describeError, useAsync } from "../../hooks/useAsync";
import { useWorkspace } from "../scenario/WorkspaceContext";
import { RuleEditor } from "./RuleEditor";
import { priorityLabel } from "./ruleControls";
import { ruleSentence } from "./ruleSentence";

type Editing = { plugin: Plugin; config?: ConstraintConfig };

export function ConstraintsPage({ scenarioId }: { scenarioId: string }) {
  const { resourceTypes } = useWorkspace();
  const catalog = useAsync(() => api.scenarioCatalog(scenarioId), [scenarioId]);
  const configs = useAsync(() => api.constraintConfigs(scenarioId), [scenarioId]);
  const weights = useAsync(() => api.constraintWeights(scenarioId), [scenarioId]);
  const resources = useAsync(() => api.resources(scenarioId), [scenarioId]);
  const demands = useAsync(() => api.demands(scenarioId), [scenarioId]);

  const [editing, setEditing] = React.useState<Editing | null>(null);
  const [error, setError] = React.useState<string>();

  const loading =
    catalog.loading || configs.loading || weights.loading || resources.loading || demands.loading;
  const loadError =
    catalog.error ?? configs.error ?? weights.error ?? resources.error ?? demands.error;

  if (loading) return <Loading />;
  if (loadError) return <ErrorBanner message={loadError} />;

  const plugins = catalog.data?.plugins ?? [];
  const allConfigs = configs.data ?? [];
  const allWeights = weights.data ?? [];

  const configurable = plugins.filter((plugin) => plugin.configDriven);
  const structural = plugins.filter((plugin) => !plugin.configDriven);

  const weightOf = (type: string) =>
    allWeights.find((weight) => weight.constraintId === type);

  async function remove(config: ConstraintConfig) {
    setError(undefined);
    try {
      await api.deleteConstraintConfig(config.id);
      configs.reload();
    } catch (cause) {
      setError(describeError(cause));
    }
  }

  return (
    <div className="space-y-4">
      {error ? <ErrorBanner message={error} /> : null}

      {editing ? (
        <RuleEditor
          scenarioId={scenarioId}
          plugin={editing.plugin}
          config={editing.config}
          resourceTypes={resourceTypes}
          resources={resources.data ?? []}
          demands={demands.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            configs.reload();
          }}
        />
      ) : null}

      {configurable.map((plugin) => (
        <FamilyCard
          key={plugin.type}
          scenarioId={scenarioId}
          plugin={plugin}
          weight={weightOf(plugin.type)}
          configs={allConfigs.filter((config) => config.type === plugin.type)}
          resourceTypes={resourceTypes}
          onWeightSaved={weights.reload}
          onAdd={() => setEditing({ plugin })}
          onEdit={(config) => setEditing({ plugin, config })}
          onDelete={remove}
        />
      ))}

      <Section
        title="Contraintes structurelles"
        description="Elles s'appliquent toujours et à tout le monde : elles n'ont rien à configurer. Seul leur poids se règle."
      >
        <div className="space-y-2">
          {structural.map((plugin) => (
            <StructuralRow
              key={plugin.type}
              scenarioId={scenarioId}
              plugin={plugin}
              weight={weightOf(plugin.type)}
              onSaved={weights.reload}
            />
          ))}
        </div>
      </Section>
    </div>
  );
}

// ─── Une famille de contrainte ─────────────────────────────────────────────

function FamilyCard(props: {
  scenarioId: string;
  plugin: Plugin;
  weight: ConstraintWeight | undefined;
  configs: ConstraintConfig[];
  resourceTypes: ResourceType[];
  onWeightSaved: () => void;
  onAdd: () => void;
  onEdit: (config: ConstraintConfig) => void;
  onDelete: (config: ConstraintConfig) => void;
}) {
  return (
    <Section
      title={props.plugin.label}
      description={props.plugin.description}
      right={
        <LevelControl
          scenarioId={props.scenarioId}
          plugin={props.plugin}
          weight={props.weight}
          ruleCount={props.configs.length}
          onSaved={props.onWeightSaved}
        />
      }
    >
      {props.configs.length === 0 ? (
        <p className="app-muted mb-3 text-sm">
          Aucune règle : cette contrainte ne s'applique à personne pour l'instant.
        </p>
      ) : (
        <ul className="mb-3 space-y-2">
          {props.configs.map((config) => {
            const { population, settings } = ruleSentence(
              props.plugin,
              config.scope,
              config.params,
              props.resourceTypes
            );
            return (
              <li key={config.id}>
                <Card>
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm">
                        <strong>{population}</strong>
                        {settings ? <span className="app-muted"> — {settings}</span> : null}
                      </p>
                      <div className="mt-1.5 flex flex-wrap items-center gap-1">
                        <Chip>{priorityLabel(config.weight)}</Chip>
                        {config.enabled ? null : <Chip tone="danger">désactivée</Chip>}
                      </div>
                    </div>
                    <div className="flex shrink-0 gap-1">
                      <Button variant="ghost" onClick={() => props.onEdit(config)}>
                        Modifier
                      </Button>
                      <Button variant="ghost" onClick={() => props.onDelete(config)}>
                        Suppr.
                      </Button>
                    </div>
                  </div>
                </Card>
              </li>
            );
          })}
        </ul>
      )}

      <Button variant="secondary" onClick={props.onAdd}>
        + Ajouter une règle
      </Button>
    </Section>
  );
}

/**
 * Le niveau est porté par la **famille**, pas par la règle — c'est délibéré : le
 * score étant lexicographique, `level` + `weight` rendent l'exclusivité
 * dur/souple structurelle (§6.12).
 *
 * <p>L'afficher en tête de famille plutôt qu'à côté de chaque règle évite de
 * laisser croire à un réglage par règle, qui produirait des surprises à la
 * résolution.
 */
function LevelControl(props: {
  scenarioId: string;
  plugin: Plugin;
  weight: ConstraintWeight | undefined;
  ruleCount: number;
  onSaved: () => void;
}) {
  const current = props.weight;
  const [level, setLevel] = React.useState<Level>(current?.level ?? props.plugin.defaultLevel);
  const [importance, setImportance] = React.useState(String(current?.weight ?? 1));
  const [saving, setSaving] = React.useState(false);
  const [error, setError] = React.useState<string>();

  React.useEffect(() => {
    setLevel(current?.level ?? props.plugin.defaultLevel);
    setImportance(String(current?.weight ?? 1));
  }, [current, props.plugin.defaultLevel]);

  const dirty = level !== current?.level || Number(importance) !== current?.weight;
  const levelChanged = current !== undefined && level !== current.level;

  async function save() {
    setSaving(true);
    setError(undefined);
    try {
      await api.upsertConstraintWeight(props.scenarioId, {
        constraintId: props.plugin.type,
        level,
        weight: Number(importance),
      });
      props.onSaved();
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1.5">
      <div className="flex flex-wrap items-center gap-2">
        <div className="app-surface-muted flex rounded-lg border p-0.5">
          {(["HARD", "SOFT"] as Level[]).map((candidate) => (
            <button
              key={candidate}
              type="button"
              onClick={() => setLevel(candidate)}
              className="rounded-md px-3 py-1.5 text-sm font-semibold transition"
              style={
                level === candidate ? { background: "var(--app-accent)", color: "white" } : undefined
              }
            >
              {candidate === "HARD" ? "Obligatoire" : "Souhaitable"}
            </button>
          ))}
        </div>

        {level === "SOFT" ? (
          <label className="flex items-center gap-1.5 text-sm">
            <span className="app-muted">Importance</span>
            <input
              className="app-input w-20 py-1.5 text-sm"
              type="number"
              min={0}
              value={importance}
              onChange={(event) => setImportance(event.target.value)}
            />
          </label>
        ) : null}

        {dirty ? (
          <Button variant="secondary" disabled={saving} onClick={save}>
            {saving ? "…" : "Appliquer"}
          </Button>
        ) : props.weight?.fromDefault ? (
          <span className="app-muted text-xs">valeur par défaut</span>
        ) : null}
      </div>

      {levelChanged && props.ruleCount > 1 ? (
        <p className="max-w-xs text-right text-xs text-amber-700">
          Ce changement s'applique aux {props.ruleCount} règles de cette famille : le
          niveau est porté par la contrainte, pas par la règle.
        </p>
      ) : null}
      {level === "HARD" && Number(importance) === 0 ? (
        <p className="text-right text-xs text-amber-700">Un poids nul désactive la contrainte.</p>
      ) : null}
      {error ? <p className="text-right text-xs text-red-600">{error}</p> : null}
    </div>
  );
}

function StructuralRow(props: {
  scenarioId: string;
  plugin: Plugin;
  weight: ConstraintWeight | undefined;
  onSaved: () => void;
}) {
  return (
    <Card>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="min-w-0">
          <p className="font-medium">{props.plugin.label}</p>
          <p className="app-muted text-xs">{props.plugin.description}</p>
        </div>
        <LevelControl
          scenarioId={props.scenarioId}
          plugin={props.plugin}
          weight={props.weight}
          ruleCount={0}
          onSaved={props.onSaved}
        />
      </div>
    </Card>
  );
}
