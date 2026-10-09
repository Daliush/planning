import type { Assignment } from "../../api/types";
import type { Shift, WorkforceOverview } from "./types";

export const ABSENCE_LABELS = {
  LEAVE: "Congé",
  SICKNESS: "Maladie",
  OTHER: "Autre absence",
};
export function addDays(date: string, days: number): string {
  const parsed = new Date(`${date}T12:00:00Z`);
  parsed.setUTCDate(parsed.getUTCDate() + days);
  return parsed.toISOString().slice(0, 10);
}
export function dateLabel(
  date: string,
  options: Intl.DateTimeFormatOptions = { day: "numeric", month: "long" },
): string {
  return new Intl.DateTimeFormat("fr-FR", {
    ...options,
    timeZone: "UTC",
  }).format(new Date(`${date}T12:00:00Z`));
}
export function periodDays(from: string, to: string): string[] {
  const days: string[] = [];
  for (let day = from; day <= to && days.length < 31; day = addDays(day, 1))
    days.push(day);
  return days;
}
export function duration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const remainder = Math.round(minutes % 60);
  return remainder
    ? `${hours} h ${String(remainder).padStart(2, "0")}`
    : `${hours} h`;
}
export const time = (value: string) => value.slice(0, 5);
export const initials = (label: string) =>
  label
    .trim()
    .split(/\s+/)
    .map((part) => part[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();
export function shiftAssignments(
  overview: WorkforceOverview,
  shift: Shift,
): Assignment[] {
  return overview.assignments.filter(
    (assignment) => assignment.demandId === shift.id,
  );
}
export function employeeMinutes(
  overview: WorkforceOverview,
  employeeId: string,
): number {
  return overview.assignments
    .filter((assignment) => assignment.resourceId === employeeId)
    .reduce(
      (minutes, assignment) =>
        minutes +
        (overview.shifts.find((shift) => shift.id === assignment.demandId)
          ?.durationMinutes ?? 0),
      0,
    );
}
export function humanError(cause: unknown): string {
  if (cause instanceof TypeError)
    return "Le service de planning est inaccessible. Vérifiez la connexion puis réessayez.";
  return cause instanceof Error
    ? cause.message
    : "L’action n’a pas pu être terminée. Réessayez.";
}
