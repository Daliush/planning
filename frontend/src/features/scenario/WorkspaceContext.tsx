import React from "react";
import { api } from "../../api/client";
import type { Catalog, ResourceType, Scenario, Tenant, TimeModel } from "../../api/types";
import { describeError } from "../../hooks/useAsync";

type Workspace = {
  tenants: Tenant[];
  tenant: Tenant | undefined;
  selectTenant: (tenantId: string) => void;
  scenarios: Scenario[];
  scenario: Scenario | undefined;
  selectScenario: (scenarioId: string) => void;
  reloadScenarios: () => void;
  resourceTypes: ResourceType[];
  timeModels: TimeModel[];
  /** Le modèle temporel du scénario courant — porte la longueur du cycle. */
  timeModel: TimeModel | undefined;
  catalog: Catalog | undefined;
  /**
   * Le catalogue filtré par l'horizon et la verticale du scénario. C'est celui
   * que les écrans de saisie doivent lire : le global propose du vocabulaire
   * d'autres verticales et des réglages que le chargement refusera.
   */
  scenarioCatalog: Catalog | undefined;
  loading: boolean;
  error: string | undefined;
};

const WorkspaceContext = React.createContext<Workspace | null>(null);

const STORAGE_KEY = "planning-scenario";

export function WorkspaceProvider({ children }: { children: React.ReactNode }) {
  const [tenants, setTenants] = React.useState<Tenant[]>([]);
  const [tenantId, setTenantId] = React.useState<string>();
  const [scenarios, setScenarios] = React.useState<Scenario[]>([]);
  const [scenarioId, setScenarioId] = React.useState<string | undefined>(
    () => window.localStorage.getItem(STORAGE_KEY) ?? undefined
  );
  const [resourceTypes, setResourceTypes] = React.useState<ResourceType[]>([]);
  const [timeModels, setTimeModels] = React.useState<TimeModel[]>([]);
  const [catalog, setCatalog] = React.useState<Catalog>();
  const [scenarioCatalog, setScenarioCatalog] = React.useState<Catalog>();
  const [loading, setLoading] = React.useState(true);
  const [error, setError] = React.useState<string>();
  const [nonce, setNonce] = React.useState(0);

  React.useEffect(() => {
    Promise.all([api.tenants(), api.catalog()])
      .then(([loadedTenants, loadedCatalog]) => {
        setTenants(loadedTenants);
        setCatalog(loadedCatalog);
        setTenantId((current) => current ?? loadedTenants[0]?.id);
      })
      .catch((cause) => setError(describeError(cause)))
      .finally(() => setLoading(false));
  }, []);

  React.useEffect(() => {
    if (!tenantId) return;
    Promise.all([api.scenarios(tenantId), api.resourceTypes(tenantId), api.timeModels(tenantId)])
      .then(([loadedScenarios, types, models]) => {
        setScenarios(loadedScenarios);
        setResourceTypes(types);
        setTimeModels(models);
        setScenarioId((current) =>
          current && loadedScenarios.some((scenario) => scenario.id === current)
            ? current
            : loadedScenarios[0]?.id
        );
      })
      .catch((cause) => setError(describeError(cause)));
  }, [tenantId, nonce]);

  /**
   * Le scénario mémorisé n'existe pas forcément encore — ou plus.
   *
   * <p>`scenarioId` est restauré depuis le `localStorage` avant tout appel réseau.
   * Interroger l'API avec un identifiant supprimé depuis rendait un `NOT_FOUND`
   * qui s'affichait en bandeau d'erreur définitif, alors même que la sélection se
   * corrigeait toute seule à l'arrivée de la liste. On n'interroge donc que ce
   * qu'on a vu exister.
   */
  const confirmedScenarioId = scenarios.some((candidate) => candidate.id === scenarioId)
    ? scenarioId
    : undefined;

  /**
   * Le catalogue du scénario suit la sélection : changer de planning change la
   * verticale, donc le vocabulaire proposé. Le vider d'abord évite qu'un écran
   * affiche une fraction de seconde les attributs du scénario précédent.
   */
  React.useEffect(() => {
    if (!confirmedScenarioId) {
      setScenarioCatalog(undefined);
      return;
    }
    let cancelled = false;
    setScenarioCatalog(undefined);
    api
      .scenarioCatalog(confirmedScenarioId)
      .then((loaded) => {
        if (!cancelled) setScenarioCatalog(loaded);
      })
      .catch((cause) => {
        if (!cancelled) setError(describeError(cause));
      });
    return () => {
      cancelled = true;
    };
  }, [confirmedScenarioId, nonce]);

  // Ne mémoriser qu'un scénario confirmé : garder un identifiant supprimé
  // rejouerait l'erreur au prochain démarrage.
  React.useEffect(() => {
    if (confirmedScenarioId) window.localStorage.setItem(STORAGE_KEY, confirmedScenarioId);
  }, [confirmedScenarioId]);

  const value = React.useMemo<Workspace>(() => {
    const scenario = scenarios.find((candidate) => candidate.id === scenarioId);
    return {
      tenants,
      tenant: tenants.find((candidate) => candidate.id === tenantId),
      selectTenant: setTenantId,
      scenarios,
      scenario,
      selectScenario: setScenarioId,
      reloadScenarios: () => setNonce((current) => current + 1),
      resourceTypes,
      timeModels,
      timeModel: timeModels.find((model) => model.id === scenario?.timeModelId),
      catalog,
      scenarioCatalog,
      loading,
      error,
    };
  }, [
    tenants,
    tenantId,
    scenarios,
    scenarioId,
    resourceTypes,
    timeModels,
    catalog,
    scenarioCatalog,
    loading,
    error,
  ]);

  return <WorkspaceContext.Provider value={value}>{children}</WorkspaceContext.Provider>;
}

export function useWorkspace(): Workspace {
  const context = React.useContext(WorkspaceContext);
  if (!context) throw new Error("useWorkspace must be used inside WorkspaceProvider");
  return context;
}
