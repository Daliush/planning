import type { SchemaProperty } from "../api/types";

/**
 * Traduction des énumérations du domaine vers le vocabulaire métier.
 *
 * <p>C'est de la présentation, pas de la règle : une valeur inconnue retombe sur
 * elle-même plutôt que de disparaître. Un plugin qui introduirait demain une
 * nouvelle énumération resterait donc utilisable, en affichant ses valeurs brutes.
 */
const ENUM_LABELS: Record<string, string> = {
  DAY: "par jour",
  WEEK: "par semaine",
  CYCLE: "sur le cycle",
  MONTH: "par mois",
  AVAILABLE: "Disponible",
  DISCOURAGED: "Déconseillé",
  UNAVAILABLE: "Indisponible",
  HARD: "Obligatoire",
  SOFT: "Souhaitable",
};

export function enumLabel(value: string): string {
  return ENUM_LABELS[value] ?? value;
}

/** « quota_hebdo_minutes » → « Quota hebdo minutes ». Repli quand `title` manque. */
export function humanizeKey(key: string): string {
  const words = key.replace(/[_-]+/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2").trim();
  return words.charAt(0).toUpperCase() + words.slice(1).toLowerCase();
}

/** L'étiquette d'un champ : le titre s'il existe, sinon la description, sinon la clé. */
export function fieldLabel(key: string, property: SchemaProperty | undefined): string {
  return property?.title ?? property?.description ?? humanizeKey(key);
}

/**
 * Pluriel prudent : « Enseignant » → « enseignants », mais « Groupe d'élèves »
 * reste inchangé. Un « s » ajouté aveuglément produisait « groupe d'élèvess ».
 */
export function pluralize(label: string): string {
  const lower = label.toLowerCase();
  return lower.endsWith("s") || lower.endsWith("x") ? lower : `${lower}s`;
}

export function formatMinutes(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${rest} min`;
  return rest === 0 ? `${hours} h` : `${hours} h ${String(rest).padStart(2, "0")}`;
}

export function splitMinutes(minutes: number): { hours: number; minutes: number } {
  return { hours: Math.floor(minutes / 60), minutes: minutes % 60 };
}

/** Rend une valeur d'attribut ou de paramètre lisible dans une phrase. */
export function formatValue(value: unknown, property?: SchemaProperty): string {
  if (value === null || value === undefined || value === "") return "—";
  if (Array.isArray(value)) return value.join(", ");
  if (property?.["x-unit"] === "minutes" && typeof value === "number") {
    return formatMinutes(value);
  }
  if (typeof value === "string") return enumLabel(value);
  return String(value);
}
