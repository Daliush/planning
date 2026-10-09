import type {
  Assignment,
  Availability,
  ConstraintConfig,
  ResourceType,
  Scenario,
  SolveJob,
  SolveReportEnvelope,
  Tenant,
} from "../../api/types";

export type WorkforceProfile = "RESTAURANT" | "COMPANY";
export type Workplace = {
  tenantId: string;
  scenarioId: string;
  label: string;
  profile: WorkforceProfile;
  from: string;
  to: string;
  employeeCount: number;
};
export type Employee = {
  id: string;
  label: string;
  skills: string[];
  contractWeeklyMinutes: number;
  overtimeWeeklyMinutes: number;
  absenceDailyMinutes: number;
  availability: Availability[];
};
export type EmployeeInput = {
  label: string;
  skills: string[];
  contractWeeklyMinutes: number;
  overtimeWeeklyMinutes?: number;
  absenceDailyMinutes?: number;
  availability?: Omit<Availability, "id">[];
};
export type ShiftRole = { role: string; quantity: number; skill?: string };
export type Shift = {
  id: string;
  label: string;
  date: string;
  startTime: string;
  endTime: string;
  nextDay: boolean;
  durationMinutes: number;
  roles: ShiftRole[];
};
export type ShiftInput = Omit<Shift, "id" | "durationMinutes">;
export type AbsenceReason = "LEAVE" | "SICKNESS" | "OTHER";
export type Absence = {
  id: string;
  resourceId: string;
  from: string;
  to: string;
  reason: AbsenceReason;
  note: string | null;
};
export type AbsenceInput = Omit<Absence, "id">;
export type WorkforcePolicy = {
  minRestMinutes: number;
  maximumDailyMinutes: number;
  balanceWorkload: boolean;
};
export type ContractWeek = {
  resourceId: string;
  from: string;
  to: string;
  contractMinutes: number;
  absenceMinutes: number;
  dueMinutes: number;
  maximumMinutes: number;
  assignedMinutes: number;
  missingMinutes: number;
  overtimeMinutes: number;
};
export type WorkforceOverview = {
  tenant: Tenant;
  scenario: Scenario;
  period: { from: string; to: string; slotMinutes: number };
  resourceTypes: ResourceType[];
  employees: Employee[];
  shifts: Shift[];
  absences: Absence[];
  latestJob: SolveJob | null;
  assignments: Assignment[];
  report: SolveReportEnvelope | null;
  planOutdated: boolean;
  contractWeeks: ContractWeek[];
  policy: WorkforcePolicy;
  rules: ConstraintConfig[];
  kpis: {
    employeeCount: number;
    shiftCount: number;
    requiredPositions: number;
    assignedPositions: number;
    unassignedPositions: number;
    coveragePercent: number;
    requiredMinutes: number;
    assignedMinutes: number;
    absenceDays: number;
  };
};
