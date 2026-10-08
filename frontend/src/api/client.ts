import type {
  Assignment,
  Availability,
  BusinessProfile,
  Catalog,
  ConstraintConfig,
  ConstraintWeight,
  Demand,
  Requirement,
  Resource,
  ResourceType,
  Scenario,
  ScheduleView,
  ScopePreview,
  ScopeTarget,
  Slot,
  SolveJob,
  SolveReportEnvelope,
  Tenant,
  TimeModel,
} from "./types";

const BASE = "/api";

/**
 * Le code d'erreur métier du backend remonte tel quel : c'est le même vocabulaire
 * que `solve_job.error_code`, et les messages sont déjà rédigés pour un humain.
 */
export class PlanningApiError extends Error {
  constructor(
    readonly code: string,
    message: string
  ) {
    super(message);
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init?.headers ?? {}) },
  });

  if (!response.ok) {
    let code = `HTTP_${response.status}`;
    let message = response.statusText;
    try {
      const body = await response.json();
      code = body.code ?? code;
      message = body.message ?? message;
    } catch {
      /* réponse sans corps JSON : on garde le statut */
    }
    throw new PlanningApiError(code, message);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

const post = <T,>(path: string, body?: unknown) =>
  request<T>(path, { method: "POST", body: body === undefined ? undefined : JSON.stringify(body) });
const put = <T,>(path: string, body: unknown) =>
  request<T>(path, { method: "PUT", body: JSON.stringify(body) });
const del = (path: string) => request<void>(path, { method: "DELETE" });

export const api = {
  catalog: () => request<Catalog>("/catalog"),

  /**
   * Le catalogue restreint à ce qui est proposable sur l'horizon du scénario.
   * C'est celui que les écrans de configuration doivent consommer : le catalogue
   * global offre des réglages que le chargement refusera.
   */
  scenarioCatalog: (scenarioId: string) => request<Catalog>(`/scenarios/${scenarioId}/catalog`),

  scopePreview: (scenarioId: string, scope: unknown, target: ScopeTarget) =>
    post<ScopePreview>(`/scenarios/${scenarioId}/scope-preview`, { scope, target }),

  tenants: () => request<Tenant[]>("/tenants"),
  resourceTypes: (tenantId: string) => request<ResourceType[]>(`/tenants/${tenantId}/resource-types`),
  timeModels: (tenantId: string) => request<TimeModel[]>(`/tenants/${tenantId}/time-models`),
  slots: (timeModelId: string) => request<Slot[]>(`/time-models/${timeModelId}/slots`),

  scenarios: (tenantId: string) => request<Scenario[]>(`/tenants/${tenantId}/scenarios`),
  scenario: (scenarioId: string) => request<Scenario>(`/scenarios/${scenarioId}`),
  createScenario: (input: {
    tenantId: string;
    label: string;
    timeModelId: string;
    parentId?: string | null;
    profile?: BusinessProfile | null;
  }) => post<Scenario>("/scenarios", input),

  /**
   * Crée un modèle temporel et sa grille. `workingDays` peut être inférieur à
   * `cycleDays` : c'est ainsi qu'un cycle de 7 jours n'ouvre que du lundi au
   * vendredi.
   */
  createTimeModel: (
    tenantId: string,
    input: {
      label: string;
      cycleDays: number;
      workingDays: number;
      dayStart: string;
      slotMinutes: number;
      slotsPerDay: number;
      maxGapMinutes: number;
      breaks: { afterOrdinal: number; minutes: number }[];
    }
  ) => post<TimeModel>(`/tenants/${tenantId}/time-models`, input),

  resources: (scenarioId: string) => request<Resource[]>(`/scenarios/${scenarioId}/resources`),
  createResource: (
    scenarioId: string,
    input: { typeId: string; label: string; defaultEffect: string; attributes: unknown }
  ) => post<Resource>(`/scenarios/${scenarioId}/resources`, input),
  updateResource: (
    resourceId: string,
    input: { typeId: string; label: string; defaultEffect: string; attributes: unknown }
  ) => put<Resource>(`/resources/${resourceId}`, input),
  deleteResource: (resourceId: string) => del(`/resources/${resourceId}`),
  replaceAvailability: (
    resourceId: string,
    rules: Omit<Availability, "id">[]
  ) => put<Availability[]>(`/resources/${resourceId}/availability`, rules),

  demands: (scenarioId: string) => request<Demand[]>(`/scenarios/${scenarioId}/demands`),
  createDemand: (scenarioId: string, input: unknown) =>
    post<Demand>(`/scenarios/${scenarioId}/demands`, input),
  updateDemand: (demandId: string, input: unknown) => put<Demand>(`/demands/${demandId}`, input),
  deleteDemand: (demandId: string) => del(`/demands/${demandId}`),
  addRequirement: (demandId: string, input: unknown) =>
    post<Requirement>(`/demands/${demandId}/requirements`, input),
  deleteRequirement: (requirementId: string) => del(`/requirements/${requirementId}`),

  constraintConfigs: (scenarioId: string) =>
    request<ConstraintConfig[]>(`/scenarios/${scenarioId}/constraint-configs`),
  createConstraintConfig: (scenarioId: string, input: unknown) =>
    post<ConstraintConfig>(`/scenarios/${scenarioId}/constraint-configs`, input),
  updateConstraintConfig: (configId: string, input: unknown) =>
    put<ConstraintConfig>(`/constraint-configs/${configId}`, input),
  deleteConstraintConfig: (configId: string) => del(`/constraint-configs/${configId}`),

  constraintWeights: (scenarioId: string) =>
    request<ConstraintWeight[]>(`/scenarios/${scenarioId}/constraint-weights`),
  upsertConstraintWeight: (
    scenarioId: string,
    input: { constraintId: string; level: string; weight: number }
  ) => put<ConstraintWeight>(`/scenarios/${scenarioId}/constraint-weights`, input),

  solveJobs: (scenarioId: string) => request<SolveJob[]>(`/scenarios/${scenarioId}/solve-jobs`),
  submitSolve: (scenarioId: string, timeLimitSeconds: number) =>
    post<SolveJob>(`/scenarios/${scenarioId}/solve-jobs`, { timeLimitSeconds }),
  solveJob: (jobId: string) => request<SolveJob>(`/solve-jobs/${jobId}`),
  schedule: (jobId: string) => request<ScheduleView>(`/solve-jobs/${jobId}/schedule`),
  assignments: (jobId: string) => request<Assignment[]>(`/solve-jobs/${jobId}/assignments`),
  report: (jobId: string) => request<SolveReportEnvelope>(`/solve-jobs/${jobId}/report`),
};
