import React from "react";
import { api } from "../../api/client";
import type { Resource } from "../../api/types";
import { Button } from "../../ui/Button";
import { EmptyState, ErrorBanner, Loading } from "../../ui/Feedback";
import { Card, Chip, Section } from "../../ui/Section";
import { describeError, useAsync } from "../../hooks/useAsync";
import { useWorkspace } from "../scenario/WorkspaceContext";
import { EFFECT_LABELS, describeRule } from "./effects";
import { ResourceEditor } from "./ResourceEditor";

export function ResourcesPage({ scenarioId }: { scenarioId: string }) {
  const { resourceTypes, scenarioCatalog, timeModel } = useWorkspace();
  const { data, loading, error, reload } = useAsync(() => api.resources(scenarioId), [scenarioId]);
  const [typeFilter, setTypeFilter] = React.useState("");
  const [editing, setEditing] = React.useState<Resource | null>(null);
  const [creating, setCreating] = React.useState(false);
  const [actionError, setActionError] = React.useState<string>();

  const resources = React.useMemo(() => {
    if (!data) return [];
    return typeFilter ? data.filter((resource) => resource.typeId === typeFilter) : data;
  }, [data, typeFilter]);

  const byType = React.useMemo(() => {
    const groups = new Map<string, Resource[]>();
    resources.forEach((resource) => {
      groups.set(resource.typeCode, [...(groups.get(resource.typeCode) ?? []), resource]);
    });
    return [...groups.entries()];
  }, [resources]);

  async function remove(resource: Resource) {
    setActionError(undefined);
    try {
      await api.deleteResource(resource.id);
      reload();
    } catch (cause) {
      setActionError(describeError(cause));
    }
  }

  if (loading) return <Loading />;
  if (error) return <ErrorBanner message={error} />;

  return (
    <div className="space-y-4">
      <Section
        title="Ressources"
        description="Ce qui existe, et quand c'est disponible. La qualification et la capacité sont des critères d'éligibilité, pas des contraintes."
        right={
          <div className="flex flex-wrap items-end gap-2">
            <select
              className="app-input w-auto py-1.5 text-sm"
              value={typeFilter}
              onChange={(event) => setTypeFilter(event.target.value)}
            >
              <option value="">Tous les types</option>
              {resourceTypes.map((type) => (
                <option key={type.id} value={type.id}>
                  {type.label}
                </option>
              ))}
            </select>
            <Button onClick={() => setCreating(true)}>Ajouter</Button>
          </div>
        }
      >
        {actionError ? <ErrorBanner message={actionError} /> : null}

        {resources.length === 0 ? (
          <EmptyState title="Aucune ressource" hint="Ajoutez un enseignant, une salle ou un groupe." />
        ) : (
          <div className="space-y-5">
            {byType.map(([typeCode, list]) => (
              <div key={typeCode}>
                <h3 className="app-muted mb-2 text-xs font-bold uppercase tracking-wide">
                  {typeCode} · {list.length}
                </h3>
                <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  {list.map((resource) => (
                    <Card key={resource.id}>
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <p className="truncate font-semibold">{resource.label}</p>
                          <p className="app-muted mt-0.5 text-xs">
                            Par défaut : {EFFECT_LABELS[resource.defaultEffect]}
                          </p>
                        </div>
                        <div className="flex shrink-0 gap-1">
                          <Button variant="ghost" onClick={() => setEditing(resource)}>
                            Modifier
                          </Button>
                          <Button variant="ghost" onClick={() => remove(resource)}>
                            Suppr.
                          </Button>
                        </div>
                      </div>

                      <div className="mt-2 flex flex-wrap gap-1">
                        {Object.entries(resource.attributes)
                          .filter(([key]) => key !== "type")
                          .map(([key, value]) => (
                            <Chip key={key}>
                              {key} : {Array.isArray(value) ? value.join(", ") : String(value)}
                            </Chip>
                          ))}
                      </div>

                      {resource.availability.length > 0 ? (
                        <ul className="app-muted mt-2 space-y-0.5 text-xs">
                          {resource.availability.map((rule) => (
                            <li key={rule.id}>• {describeRule(rule, timeModel?.cycleDays ?? undefined)}</li>
                          ))}
                        </ul>
                      ) : null}
                    </Card>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      {creating || editing ? (
        <ResourceEditor
          scenarioId={scenarioId}
          resource={editing ?? undefined}
          resourceTypes={resourceTypes}
          attributeDefinitions={scenarioCatalog?.attributes ?? []}
          // Le repli à 7 ne sert qu'au premier rendu, avant que le modèle
          // temporel soit chargé : la modale n'est ouverte qu'ensuite.
          cycleDays={timeModel?.cycleDays ?? 7}
          onClose={() => {
            setCreating(false);
            setEditing(null);
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
