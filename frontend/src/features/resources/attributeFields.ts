import type {
  AttributeDefinition,
  AttributeKind,
  JsonSchema,
  SchemaProperty,
} from "../../api/types";
import { fieldLabel } from "../../utils/labels";

/**
 * Un champ du formulaire d'attributs, quelle que soit sa provenance.
 *
 * <p>`source` sert uniquement à expliquer d'où vient le champ dans l'interface :
 * un attribut hors verticale mérite d'être signalé comme tel plutôt que d'être
 * mélangé au vocabulaire courant.
 */
export type AttributeField = {
  key: string;
  title: string;
  description?: string;
  kind: AttributeKind;
  options: string[];
  minimum: number | null;
  readBy: string[];
  /** `catalog` : vocabulaire du produit · `schema` : propre au tenant · `value` : saisi librement. */
  source: "catalog" | "schema" | "value";
};

/**
 * Traduit une propriété JSON Schema en forme d'attribut.
 *
 * <p>L'ordre des tests compte : un `enum` est une énumération même s'il déclare
 * aussi `type: string`, et une durée est un entier avant d'être une durée. Tester
 * `type` en premier ferait tomber les deux dans le mauvais widget.
 */
function kindOfProperty(property: SchemaProperty): AttributeKind {
  if (property.enum && property.enum.length > 0) return "ENUM";
  if (property.type === "array") return "TEXT_LIST";
  if (property["x-unit"] === "minutes") return "DURATION_MINUTES";
  if (property.type === "integer" || property.type === "number") return "INTEGER";
  return "TEXT";
}

/**
 * Devine la forme d'une clé que ni le catalogue ni le schéma ne déclarent.
 *
 * <p>C'est le cas d'un attribut posé à la main par l'éditeur avancé. Le deviner
 * vaut mieux que de le cacher : une clé invisible dans le formulaire serait
 * silencieusement écrasée à l'enregistrement.
 */
function kindOfValue(value: unknown): AttributeKind {
  if (Array.isArray(value)) return "TEXT_LIST";
  if (typeof value === "number") return "INTEGER";
  return "TEXT";
}

/**
 * Les champs à afficher, par union de trois sources.
 *
 * <p>Le catalogue l'emporte : c'est le vocabulaire du produit, celui que les
 * plugins et les scopes savent exploiter. Le schéma du type complète pour les
 * clés propres au tenant. Les clés présentes dans la valeur mais déclarées nulle
 * part ferment la marche — sans elles, ouvrir puis enregistrer une ressource
 * perdrait ses attributs exotiques.
 */
export function attributeFields(
  definitions: AttributeDefinition[],
  schema: JsonSchema | undefined,
  value: Record<string, unknown>,
  resourceTypeCode: string | undefined
): AttributeField[] {
  const fields = new Map<string, AttributeField>();

  definitions
    .filter(
      (definition) =>
        definition.resourceTypes.length === 0 ||
        (resourceTypeCode !== undefined && definition.resourceTypes.includes(resourceTypeCode))
    )
    .forEach((definition) =>
      fields.set(definition.key, {
        key: definition.key,
        title: definition.title,
        description: definition.description,
        kind: definition.kind,
        options: definition.options,
        minimum: definition.minimum,
        readBy: definition.readBy,
        source: "catalog",
      })
    );

  Object.entries(schema?.properties ?? {}).forEach(([key, property]) => {
    if (fields.has(key)) return;
    fields.set(key, {
      key,
      title: fieldLabel(key, property),
      description: property.description,
      kind: kindOfProperty(property),
      options: property.enum ?? [],
      minimum: property.minimum ?? null,
      readBy: [],
      source: "schema",
    });
  });

  Object.keys(value).forEach((key) => {
    if (fields.has(key)) return;
    fields.set(key, {
      key,
      title: fieldLabel(key, undefined),
      kind: kindOfValue(value[key]),
      options: [],
      minimum: null,
      readBy: [],
      source: "value",
    });
  });

  return [...fields.values()];
}

/**
 * Retire les champs vides avant l'envoi.
 *
 * <p>Un attribut à `""` ou `[]` n'est pas « vide » pour le moteur de critères :
 * il existe, et un `contains` sur un tableau vide rend `false` là où l'absence de
 * clé rend aussi `false` — mais un `eq` sur `""` matcherait une ressource dont on
 * croyait l'attribut non renseigné. Ne pas écrire la clé est plus honnête.
 */
export function pruneEmpty(attributes: Record<string, unknown>): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  Object.entries(attributes).forEach(([key, value]) => {
    if (value === null || value === undefined || value === "") return;
    if (Array.isArray(value) && value.length === 0) return;
    result[key] = value;
  });
  return result;
}

/** « maths, physique » → `["maths", "physique"]`, sans entrée vide ni doublon. */
export function parseList(raw: string): string[] {
  const items = raw
    .split(",")
    .map((item) => item.trim())
    .filter((item) => item !== "");
  return [...new Set(items)];
}
