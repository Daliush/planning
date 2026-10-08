import type { Plugin, ResourceType } from "../../api/types";
import { fieldLabel, formatValue, humanizeKey } from "../../utils/labels";
import { describeScope, parseScope } from "./scopeModel";

/**
 * Une règle rendue comme une phrase.
 *
 * <p>« Le code fournit les mots, la base contient les phrases » (§2.1). Autant
 * afficher la phrase plutôt que la ligne de base de données qui la porte.
 *
 * <p>La construction reste générique — elle lit le schéma du plugin, jamais son
 * identifiant — pour qu'un plugin ajouté demain soit lisible sans retoucher le
 * frontend.
 */
export function ruleSentence(
  plugin: Plugin,
  scope: unknown,
  params: Record<string, unknown>,
  resourceTypes: ResourceType[]
): { population: string; settings: string } {
  const typeLabel = (code: string) =>
    resourceTypes.find((type) => type.code === code)?.label ?? code;

  const attributeLabel = (key: string) => {
    for (const type of resourceTypes) {
      const property = type.attrSchema?.properties?.[key];
      if (property) return fieldLabel(key, property);
    }
    return humanizeKey(key);
  };

  const population =
    plugin.scopeTarget === "DEMAND"
      ? describeDemandScope(scope, attributeLabel)
      : describeScope(parseScope(scope), typeLabel, attributeLabel);

  const properties = plugin.paramSchema?.properties ?? {};
  const settings = Object.entries(properties)
    .filter(([key]) => params[key] !== undefined && params[key] !== "")
    .map(([key, property]) => `${fieldLabel(key, property)} : ${formatValue(params[key], property)}`)
    .join(" · ");

  return { population, settings };
}

function describeDemandScope(scope: unknown, attributeLabel: (key: string) => string): string {
  const shape = parseScope(scope);
  if (shape.kind === "all") return "Toutes les demandes";
  if (shape.kind === "raw") return "Sélection avancée";
  if (!shape.attribute || shape.value === "") return "Toutes les demandes";

  const verbe = shape.operator === "contains" ? "contient" : "est";
  return `Les demandes dont ${attributeLabel(shape.attribute).toLowerCase()} ${verbe} « ${shape.value} »`;
}
