/**
 * Traduction entre le JSON d'un scope et le modèle simplifié de l'éditeur.
 *
 * <p>Le moteur de critères est plus expressif que n'importe quel formulaire :
 * `and` / `or` / `not` imbriqués, comparaisons numériques, `containsAll`.
 * L'éditeur couvre les formes courantes — un type, un attribut, ou les deux — et
 * **rend la main au mode JSON dès qu'il rencontre autre chose**.
 *
 * <p>C'est délibéré : mieux vaut un éditeur qui avoue son incompétence qu'un
 * éditeur qui écrase silencieusement une règle qu'il n'a pas su lire.
 */

import { pluralize } from "../../utils/labels";

export type ScopeOperator = "eq" | "contains";

export type ScopeShape =
  | { kind: "all" }
  | {
      kind: "simple";
      typeCode: string | null;
      attribute: string | null;
      operator: ScopeOperator;
      value: string;
    }
  /** Le formulaire ne sait pas représenter ce scope ; seul le mode JSON l'édite. */
  | { kind: "raw" };

type Condition = { op: ScopeOperator; path: string; value: string };

function readCondition(node: unknown): Condition | null {
  if (!node || typeof node !== "object" || Array.isArray(node)) return null;
  const entries = Object.entries(node as Record<string, unknown>);
  if (entries.length !== 1) return null;

  const [op, body] = entries[0];
  if (op !== "eq" && op !== "contains") return null;
  if (!body || typeof body !== "object") return null;

  const { path, value } = body as { path?: unknown; value?: unknown };
  if (typeof path !== "string" || !path.startsWith("/")) return null;
  if (typeof value !== "string" && typeof value !== "number") return null;

  return { op, path: path.slice(1), value: String(value) };
}

function condition(op: ScopeOperator, path: string, value: string) {
  return { [op]: { path: `/${path}`, value } };
}

export function parseScope(scope: unknown): ScopeShape {
  if (!scope || typeof scope !== "object" || Object.keys(scope).length === 0) {
    return { kind: "all" };
  }

  const entries = Object.entries(scope as Record<string, unknown>);
  if (entries.length !== 1) return { kind: "raw" };
  const [operator, body] = entries[0];

  if (operator === "and") {
    if (!Array.isArray(body) || body.length !== 2) return { kind: "raw" };
    const first = readCondition(body[0]);
    const second = readCondition(body[1]);
    if (!first || !second || first.path !== "type" || second.path === "type") {
      return { kind: "raw" };
    }
    return {
      kind: "simple",
      typeCode: first.value,
      attribute: second.path,
      operator: second.op,
      value: second.value,
    };
  }

  const single = readCondition(scope);
  if (!single) return { kind: "raw" };

  return single.path === "type"
    ? { kind: "simple", typeCode: single.value, attribute: null, operator: "eq", value: "" }
    : {
        kind: "simple",
        typeCode: null,
        attribute: single.path,
        operator: single.op,
        value: single.value,
      };
}

export function buildScope(shape: ScopeShape): unknown {
  if (shape.kind !== "simple") return {};

  const hasType = Boolean(shape.typeCode);
  const hasAttribute = Boolean(shape.attribute) && shape.value !== "";

  if (hasType && hasAttribute) {
    return {
      and: [
        condition("eq", "type", shape.typeCode as string),
        condition(shape.operator, shape.attribute as string, shape.value),
      ],
    };
  }
  if (hasType) return condition("eq", "type", shape.typeCode as string);
  if (hasAttribute) return condition(shape.operator, shape.attribute as string, shape.value);
  return {};
}

/** Le scope rendu en français, pour la phrase qui résume la règle. */
export function describeScope(
  shape: ScopeShape,
  typeLabel: (code: string) => string,
  attributeLabel: (key: string) => string
): string {
  if (shape.kind === "all") return "Toutes les ressources";
  if (shape.kind === "raw") return "Sélection avancée";

  const population = shape.typeCode
    ? `Les ${pluralize(typeLabel(shape.typeCode))}`
    : "Les ressources";
  if (!shape.attribute || shape.value === "") return population;

  const verbe = shape.operator === "contains" ? "contient" : "est";
  return `${population} dont ${attributeLabel(shape.attribute).toLowerCase()} ${verbe} « ${shape.value} »`;
}
