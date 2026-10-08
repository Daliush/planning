import React from "react";
import { api } from "../../api/client";
import { Button } from "../../ui/Button";
import { describeError } from "../../hooks/useAsync";
import { ScenarioCreator } from "./ScenarioCreator";
import { useWorkspace } from "./WorkspaceContext";

/**
 * Le scénario isole chaque instance de planification : poids, catalogue actif et
 * modèle temporel sont portés par lui, ce qui permet de comparer plusieurs
 * plannings candidats sans se marcher dessus (§4.5).
 */
export function ScenarioPicker() {
  const {
    tenants,
    tenant,
    selectTenant,
    scenarios,
    scenario,
    selectScenario,
    reloadScenarios,
    timeModels,
  } = useWorkspace();
  const [deriving, setDeriving] = React.useState(false);
  const [creating, setCreating] = React.useState(false);
  const [error, setError] = React.useState<string>();

  async function derive() {
    if (!scenario || !tenant) return;
    setDeriving(true);
    setError(undefined);
    try {
      const created = await api.createScenario({
        tenantId: tenant.id,
        label: `${scenario.label} — variante`,
        timeModelId: scenario.timeModelId,
        parentId: scenario.id,
        // Une variante compare deux réglages du même planning : elle hérite de
        // la verticale, sans quoi elle repartirait en mode expert.
        profile: scenario.profile,
      });
      reloadScenarios();
      selectScenario(created.id);
    } catch (cause) {
      setError(describeError(cause));
    } finally {
      setDeriving(false);
    }
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <div className="flex flex-wrap items-center gap-2">
        {tenants.length > 1 ? (
          <select
            className="app-input w-auto py-1.5 text-sm"
            value={tenant?.id ?? ""}
            onChange={(event) => selectTenant(event.target.value)}
          >
            {tenants.map((candidate) => (
              <option key={candidate.id} value={candidate.id}>
                {candidate.label}
              </option>
            ))}
          </select>
        ) : null}

        <select
          className="app-input w-auto py-1.5 text-sm"
          value={scenario?.id ?? ""}
          onChange={(event) => selectScenario(event.target.value)}
        >
          {scenarios.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.label}
            </option>
          ))}
        </select>

        <Button variant="secondary" onClick={derive} disabled={deriving || !scenario}
                title="Créer une variante à partir du scénario courant">
          Dériver
        </Button>

        <Button onClick={() => setCreating(true)} disabled={!tenant}
                title="Créer un planning avec son propre rythme et sa verticale">
          Nouveau
        </Button>
      </div>
      {error ? <span className="text-xs text-red-600">{error}</span> : null}

      {creating && tenant ? (
        <ScenarioCreator
          tenantId={tenant.id}
          tenantProfile={tenant.profile}
          timeModels={timeModels}
          onClose={() => setCreating(false)}
          onCreated={(scenarioId) => {
            setCreating(false);
            reloadScenarios();
            selectScenario(scenarioId);
          }}
        />
      ) : null}
    </div>
  );
}
