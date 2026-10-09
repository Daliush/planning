import { request } from "./client";
import type {
  Absence,
  AbsenceInput,
  Employee,
  EmployeeInput,
  Shift,
  ShiftInput,
  WorkforceOverview,
  WorkforcePolicy,
  WorkforceProfile,
  Workplace,
} from "../features/workforce/types";

const base = "/workforce";
const save = <T>(path: string, method: "POST" | "PUT", input: unknown) =>
  request<T>(`${base}${path}`, { method, body: JSON.stringify(input) });
const remove = (path: string) =>
  request<void>(`${base}${path}`, { method: "DELETE" });

export const workforceApi = {
  workspaces: (profile: WorkforceProfile) =>
    request<Workplace[]>(`${base}/workspaces?profile=${profile}`),
  createWorkspace: (input: {
    label: string;
    profile: WorkforceProfile;
    from: string;
    to: string;
    demo: boolean;
  }) =>
    save<{ tenantId: string; scenarioId: string }>(
      "/workspaces",
      "POST",
      input,
    ),
  overview: (id: string) =>
    request<WorkforceOverview>(`${base}/scenarios/${id}/overview`),
  saveEmployee: (scenarioId: string, input: EmployeeInput, id?: string) =>
    save<Employee>(
      `/scenarios/${scenarioId}/employees${id ? `/${id}` : ""}`,
      id ? "PUT" : "POST",
      input,
    ),
  deleteEmployee: (scenarioId: string, id: string) =>
    remove(`/scenarios/${scenarioId}/employees/${id}`),
  saveShift: (scenarioId: string, input: ShiftInput, id?: string) =>
    save<Shift>(
      `/scenarios/${scenarioId}/shifts${id ? `/${id}` : ""}`,
      id ? "PUT" : "POST",
      input,
    ),
  deleteShift: (scenarioId: string, id: string) =>
    remove(`/scenarios/${scenarioId}/shifts/${id}`),
  createAbsence: (scenarioId: string, input: AbsenceInput) =>
    save<Absence>(`/scenarios/${scenarioId}/absences`, "POST", input),
  deleteAbsence: (scenarioId: string, id: string) =>
    remove(`/scenarios/${scenarioId}/absences/${id}`),
  savePolicy: (scenarioId: string, input: WorkforcePolicy) =>
    save<WorkforcePolicy>(`/scenarios/${scenarioId}/policy`, "PUT", input),
};
