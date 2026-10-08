import type { Availability, Effect } from "../../api/types";
import { dayLabel, shortTime } from "../../utils/format";

export const EFFECT_LABELS: Record<Effect, string> = {
  AVAILABLE: "Disponible",
  DISCOURAGED: "Déconseillé",
  UNAVAILABLE: "Indisponible",
};

/** Une règle de disponibilité rendue en clair, pour la liste des ressources. */
export function describeRule(rule: Availability, cycleDays?: number): string {
  const when = rule.dayInCycle === null ? "tous les jours" : dayLabel(rule.dayInCycle, cycleDays);
  const from = rule.startTime ? ` de ${shortTime(rule.startTime)}` : "";
  const to = rule.endTime ? ` à ${shortTime(rule.endTime)}` : from ? " jusqu'à la fermeture" : "";
  return `${EFFECT_LABELS[rule.effect]} — ${when}${from}${to} (priorité ${rule.priority})`;
}
