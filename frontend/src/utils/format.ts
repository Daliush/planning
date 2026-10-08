const DAY_NAMES = ["lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"];

/**
 * Le rang d'une semaine dans le cycle, dit comme on le dit à l'oral.
 *
 * <p>« 2e lundi » se comprend sans légende, là où « lundi (S2) » demande de
 * deviner ce que S veut dire.
 */
function weekOrdinal(week: number): string {
  return week === 1 ? "1er" : `${week}e`;
}

/**
 * Le nom d'un jour du cycle.
 *
 * <p>ATTENTION — ce n'est pas un jour de calendrier. `day_in_cycle` est un rang
 * dans le cycle, de 0 à `cycleDays - 1` ; le nom de jour n'est qu'un confort
 * d'affichage, calculé comme côté backend (`dayInCycle % 7`). Aucune contrainte
 * ne dépend du jour de la semaine (§7.1) — un cycle de 3 jours ou de 10 jours est
 * parfaitement légitime, et ses « lundis » ne tombent pas un lundi.
 *
 * <p>Sans `cycleDays`, le suffixe de semaine n'apparaît qu'au-delà du 7e jour :
 * c'est le repli des appelants qui n'ont pas le modèle temporel sous la main.
 */
export function dayLabel(dayInCycle: number, cycleDays?: number): string {
  const name = DAY_NAMES[dayInCycle % 7];
  const singleWeek = cycleDays === undefined ? dayInCycle < 7 : cycleDays <= 7;
  return singleWeek ? name : `${weekOrdinal(Math.floor(dayInCycle / 7) + 1)} ${name}`;
}

export function capitalize(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

/**
 * Les jours proposables d'un cycle, prêts pour une liste déroulante.
 *
 * <p>C'est ce qui remplace la saisie d'un entier nu : personne ne sait de tête
 * que le mercredi d'un cycle de deux semaines porte le numéro 9.
 */
export function cycleDayOptions(cycleDays: number): { value: number; label: string }[] {
  return Array.from({ length: Math.max(cycleDays, 0) }, (_, day) => ({
    value: day,
    label: capitalize(dayLabel(day, cycleDays)),
  }));
}

export function hoursMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${String(rest).padStart(2, "0")}`;
}

/** « 08:00:00 » → « 08:00 ». */
export function shortTime(time: string): string {
  return time.slice(0, 5);
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value ?? {}, null, 2);
}

export function parseJson(raw: string): unknown {
  return JSON.parse(raw.trim() === "" ? "{}" : raw);
}

/** Le score est lexicographique : le dur domine, le souple ne départage qu'à égalité. */
export function scoreLabel(hard: number | null, soft: number | null): string {
  if (hard === null || soft === null) return "—";
  return `${hard} dur / ${soft} souple`;
}
