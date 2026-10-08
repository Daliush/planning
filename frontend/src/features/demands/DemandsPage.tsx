import React from "react";
import { api } from "../../api/client";
import type { Demand } from "../../api/types";
import { Button } from "../../ui/Button";
import { EmptyState, ErrorBanner, Loading } from "../../ui/Feedback";
import { Card, Chip, Section } from "../../ui/Section";
import { describeError, useAsync } from "../../hooks/useAsync";
import { hoursMinutes } from "../../utils/format";
import { useWorkspace } from "../scenario/WorkspaceContext";
import { DemandEditor } from "./DemandEditor";

export function DemandsPage({ scenarioId }: { scenarioId: string }) {
  const { resourceTypes } = useWorkspace();
  const { data, loading, error, reload } = useAsync(() => api.demands(scenarioId), [scenarioId]);
  const [editing, setEditing] = React.useState<Demand | null>(null);
  const [creating, setCreating] = React.useState(false);
  const [actionError, setActionError] = React.useState<string>();

  async function remove(demand: Demand) {
    setActionError(undefined);
    try {
      await api.deleteDemand(demand.id);
      reload();
    } catch (cause) {
      setActionError(describeError(cause));
    }
  }

  if (loading) return <Loading />;
  if (error) return <ErrorBanner message={error} />;

  const demands = data ?? [];
  const totalSessions = demands.reduce((sum, demand) => sum + demand.occurrences, 0);

  return (
    <div className="space-y-4">
      <Section
        title="Demandes"
        description="Ce qu'il faut planifier. Une demande crée des cases à remplir ; une contrainte juge une affectation qui existe."
        right={<Button onClick={() => setCreating(true)}>Ajouter</Button>}
      >
        {actionError ? <ErrorBanner message={actionError} /> : null}

        <p className="app-muted mb-3 text-sm">
          {demands.length} demande(s) · {totalSessions} séance(s) à placer par cycle
        </p>

        {demands.length === 0 ? (
          <EmptyState title="Aucune demande" hint="Ajoutez un cours à placer." />
        ) : (
          <div className="grid gap-2 lg:grid-cols-2">
            {demands.map((demand) => (
              <Card key={demand.id}>
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="truncate font-semibold">{demand.label}</p>
                    <p className="app-muted mt-0.5 text-xs">
                      {demand.occurrences} × {hoursMinutes(demand.durationMinutes)} · placement
                      « {String(demand.placement.strategy ?? "free")} »
                    </p>
                  </div>
                  <div className="flex shrink-0 gap-1">
                    <Button variant="ghost" onClick={() => setEditing(demand)}>
                      Modifier
                    </Button>
                    <Button variant="ghost" onClick={() => remove(demand)}>
                      Suppr.
                    </Button>
                  </div>
                </div>

                <div className="mt-2 flex flex-wrap gap-1">
                  {Object.entries(demand.attributes).map(([key, value]) => (
                    <Chip key={key}>
                      {key} : {String(value)}
                    </Chip>
                  ))}
                </div>

                <ul className="app-muted mt-2 space-y-0.5 text-xs">
                  {demand.requirements.map((requirement) => (
                    <li key={requirement.id}>
                      • {requirement.quantity} × <strong>{requirement.role}</strong> (
                      {requirement.typeCode}) — {JSON.stringify(requirement.criteria)}
                    </li>
                  ))}
                  {demand.requirements.length === 0 ? (
                    <li className="text-red-600">• Aucun poste : rien ne sera affecté.</li>
                  ) : null}
                </ul>
              </Card>
            ))}
          </div>
        )}
      </Section>

      {creating || editing ? (
        <DemandEditor
          scenarioId={scenarioId}
          demand={editing ?? undefined}
          resourceTypes={resourceTypes}
          onClose={() => {
            setCreating(false);
            setEditing(null);
            // Les postes s'ajoutent immédiatement : la liste doit refléter ces
            // changements même si le formulaire n'a pas été enregistré.
            reload();
          }}
          onSaved={() => {
            setCreating(false);
            setEditing(null);
            reload();
          }}
        />
      ) : null}
    </div>
  );
}
