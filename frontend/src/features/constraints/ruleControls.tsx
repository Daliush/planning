import React from "react";
import { JsonField } from "../../ui/Field";
import { parseJson, prettyJson } from "../../utils/format";

/**
 * Contrôles partagés par la liste des règles et leur éditeur.
 *
 * <p>Extraits dans leur propre module pour rompre le cycle d'imports entre les
 * deux : un cycle typecheckerait, mais laisserait une des deux constantes à
 * `undefined` à l'initialisation selon l'ordre de chargement.
 */

/** Le poids ② en crans nommés. Sa valeur reste visible : c'est elle qui compte. */
export const PRIORITIES = [
  { value: 1, label: "Normale" },
  { value: 3, label: "Importante" },
  { value: 6, label: "Prioritaire" },
];

export function priorityLabel(weight: number): string {
  return PRIORITIES.find((priority) => priority.value === weight)?.label
    ?? `Personnalisée (${weight})`;
}

/**
 * Conservé pour les scopes et réglages que le formulaire ne sait pas représenter.
 * Le modèle est plus expressif que n'importe quel formulaire ; l'amputer serait
 * pire que d'exposer du JSON à ceux qui en ont besoin.
 */
export function AdvancedJson(props: {
  scope: unknown;
  params: unknown;
  onScope: (value: unknown) => void;
  onParams: (value: unknown) => void;
}) {
  const [open, setOpen] = React.useState(false);
  const [scopeText, setScopeText] = React.useState(prettyJson(props.scope));
  const [paramsText, setParamsText] = React.useState(prettyJson(props.params));

  // Tant que le dépliant est fermé, le texte suit les formulaires. Une fois
  // ouvert, l'utilisateur reprend la main et on ne l'écrase plus sous ses doigts.
  React.useEffect(() => {
    if (!open) {
      setScopeText(prettyJson(props.scope));
      setParamsText(prettyJson(props.params));
    }
  }, [open, props.scope, props.params]);

  return (
    <div className="app-divider border-t pt-3">
      <button
        type="button"
        className="app-muted text-xs font-semibold"
        onClick={() => setOpen((value) => !value)}
      >
        {open ? "▾" : "▸"} Édition avancée (JSON)
      </button>
      {open ? (
        <div className="mt-3 grid gap-3 sm:grid-cols-2">
          <JsonField
            label="Filtre"
            value={scopeText}
            rows={5}
            onChange={(value) => {
              setScopeText(value);
              try {
                props.onScope(parseJson(value));
              } catch {
                /* saisie en cours : on n'écrase pas tant que ce n'est pas valide */
              }
            }}
          />
          <JsonField
            label="Réglages"
            value={paramsText}
            rows={5}
            onChange={(value) => {
              setParamsText(value);
              try {
                props.onParams(parseJson(value) as Record<string, unknown>);
              } catch {
                /* idem */
              }
            }}
          />
        </div>
      ) : null}
    </div>
  );
}
