export type Effect = "AVAILABLE" | "DISCOURAGED" | "UNAVAILABLE";
export type Level = "HARD" | "SOFT";
export type JobStatus = "QUEUED" | "RUNNING" | "DONE" | "FAILED" | "CANCELLED";

/**
 * La verticale métier. Elle ne change rien au moteur : elle choisit seulement le
 * vocabulaire d'attributs que le formulaire propose. `null` sur un scénario
 * signifie « ne rien filtrer » — le mode expert, qui voit tout le vocabulaire.
 */
export type BusinessProfile = "SCHOOL" | "COMPANY" | "RESTAURANT";

export type Tenant = { id: string; code: string; label: string; profile: BusinessProfile };

/** La forme d'un attribut métier — décide du widget et de l'opérateur qui a du sens. */
export type AttributeKind = "TEXT" | "TEXT_LIST" | "INTEGER" | "DURATION_MINUTES" | "ENUM";

/**
 * Un attribut métier connu du produit, tel que le backend le déclare.
 *
 * `readBy` liste les plugins qui lisent la clé directement — calculé côté serveur
 * depuis le code des plugins, jamais saisi. Vide dans l'immense majorité des cas :
 * les règles passent par les scopes, qui sont des données.
 */
export type AttributeDefinition = {
  key: string;
  title: string;
  description: string;
  kind: AttributeKind;
  options: string[];
  minimum: number | null;
  profiles: BusinessProfile[];
  resourceTypes: string[];
  readBy: string[];
};

/**
 * Le sous-ensemble de JSON Schema que le backend émet, plus le marqueur d'unité
 * `x-unit`. Sans lui, une durée en minutes et un simple compteur produisent
 * exactement le même schéma, et le formulaire ne peut pas choisir son widget
 * sans coder en dur une convention de nommage.
 */
export type SchemaProperty = {
  type?: "integer" | "number" | "string" | "array" | "boolean";
  title?: string;
  description?: string;
  minimum?: number;
  enum?: string[];
  items?: { type?: string };
  "x-unit"?: "minutes" | "timeOfDay";
};

export type JsonSchema = {
  type?: string;
  properties?: Record<string, SchemaProperty>;
  required?: string[];
};

export type ResourceType = {
  id: string;
  tenantId: string;
  code: string;
  label: string;
  attrSchema: JsonSchema;
};

export type TimeModel = {
  id: string;
  tenantId: string;
  label: string;
  horizonKind: "CYCLIC" | "DATED";
  cycleDays: number | null;
  maxGapMinutes: number;
  slotCount: number;
};

export type Slot = {
  index: number;
  dayInCycle: number;
  ordinal: number;
  startTime: string;
  endTime: string;
};

export type Scenario = {
  id: string;
  tenantId: string;
  label: string;
  timeModelId: string;
  timeModelLabel: string | null;
  status: string;
  parentId: string | null;
  profile: BusinessProfile | null;
  createdAt: string;
};

export type Availability = {
  id: string;
  effect: Effect;
  dayInCycle: number | null;
  startTime: string | null;
  endTime: string | null;
  priority: number;
};

export type Resource = {
  id: string;
  scenarioId: string;
  typeId: string;
  typeCode: string;
  label: string;
  defaultEffect: Effect;
  attributes: Record<string, unknown>;
  availability: Availability[];
};

export type Requirement = {
  id: string;
  demandId: string;
  role: string;
  typeId: string;
  typeCode: string;
  quantity: number;
  criteria: Record<string, unknown>;
};

export type Demand = {
  id: string;
  scenarioId: string;
  label: string;
  durationMinutes: number;
  occurrences: number;
  attributes: Record<string, unknown>;
  placement: Record<string, unknown>;
  requirements: Requirement[];
};

export type ConstraintConfig = {
  id: string;
  scenarioId: string;
  type: string;
  typeLabel: string;
  scope: Record<string, unknown>;
  params: Record<string, unknown>;
  weight: number;
  enabled: boolean;
};

export type ConstraintWeight = {
  constraintId: string;
  label: string;
  level: Level;
  weight: number;
  fromDefault: boolean;
};

export type ScopeTarget = "RESOURCE" | "DEMAND";

export type Plugin = {
  type: string;
  label: string;
  description: string;
  defaultLevel: Level;
  configDriven: boolean;
  /** Contre quoi le scope est évalué : les ressources, ou les demandes. */
  scopeTarget: ScopeTarget;
  horizons: string[];
  paramSchema: JsonSchema;
};

export type ScopeMatch = { id: string; label: string; typeCode: string | null };

/** Qui une règle vise réellement, évalué par le backend sur les données du scénario. */
export type ScopePreview = { matched: number; total: number; samples: ScopeMatch[] };

export type Catalog = {
  plugins: Plugin[];
  placements: { id: string; paramSchema: Record<string, unknown> }[];
  criterionOperators: string[];
  periods: string[];
  effects: Effect[];
  /** Le vocabulaire métier proposable, déjà filtré par la verticale du scénario. */
  attributes: AttributeDefinition[];
};

export type SolveJob = {
  id: string;
  scenarioId: string;
  status: JobStatus;
  timeLimitSeconds: number;
  hardScore: number | null;
  softScore: number | null;
  feasible: boolean;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  errorCode: string | null;
  errorDetail: string | null;
};

export type Assignment = {
  id: string;
  demandId: string;
  demandLabel: string | null;
  occurrence: number;
  resourceId: string | null;
  resourceLabel: string | null;
  resourceTypeCode: string | null;
  role: string;
  dayInCycle: number;
  startTime: string;
  endTime: string;
  pinned: boolean;
};

export type ScheduleView = {
  job: SolveJob;
  cycleDays: number;
  slots: Slot[];
  resources: { id: string; label: string; typeCode: string }[];
  assignments: Assignment[];
};

export type Violation = {
  constraintId: string;
  level: Level;
  penalty: number;
  message: string;
  resourceIds: string[];
  activityIds: string[];
  context: Record<string, unknown>;
};

export type SolveReport = {
  hardScore: number;
  softScore: number;
  feasible: boolean;
  hardViolations: Violation[];
  softViolations: Violation[];
  penaltyByConstraint: Record<string, number>;
};

export type SolveReportEnvelope = {
  report: SolveReport;
  constraintLabels: Record<string, string>;
};

export type ApiError = { code: string; message: string };
