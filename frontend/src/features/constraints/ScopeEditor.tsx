import React from "react";
import { api } from "../../api/client";
import type { Demand, Resource, ResourceType, ScopePreview, ScopeTarget } from "../../api/types";
import { Chip } from "../../ui/Section";
import { fieldLabel, humanizeKey } from "../../utils/labels";
import { buildScope, parseScope, type ScopeShape } from "./scopeModel";

type Option = { value: string; label: string };

/**
 * L'éditeur du « à qui » d'une règle.
 *
 * <p>Les attributs proposés viennent de `attr_schema`, les valeurs de son `enum`
 * quand il en déclare un, et à défaut de ce qui existe réellement dans le
 * scénario. Rien n'est codé en dur : un nouveau type de ressource apparaît ici
 * sans toucher au frontend.
 */
export function ScopeEditor(props: {
  scenarioId: string;
  target: ScopeTarget;
  resourceTypes: ResourceType[];
  resources: Resource[];
  demands: Demand[];
  value: unknown;
  onChange: (scope: unknown) => void;
}) {
  const shape = React.useMemo(() => parseScope(props.value), [props.value]);
  const [preview, setPreview] = React.useState<ScopePreview>();
  const [previewError, setPreviewError] = React.useState<string>();
  const [expanded, setExpanded] = React.useState(false);

  const onDemands = props.target === "DEMAND";

  // ─── Les options, déduites du schéma puis des données ────────────────────

  const typeOptions: Option[] = React.useMemo(
    () => props.resourceTypes.map((type) => ({ value: type.code, label: type.label })),
    [props.resourceTypes]
  );

  const selectedType = React.useMemo(
    () =>
      shape.kind === "simple" && shape.typeCode
        ? props.resourceTypes.find((type) => type.code === shape.typeCode)
        : undefined,
    [shape, props.resourceTypes]
  );

  const attributeOptions: Option[] = React.useMemo(() => {
    if (onDemands) {
      const keys = new Set<string>();
      props.demands.forEach((demand) => Object.keys(demand.attributes).forEach((k) => keys.add(k)));
      return [...keys].sort().map((key) => ({ value: key, label: humanizeKey(key) }));
    }
    const sources = selectedType ? [selectedType] : props.resourceTypes;
    const seen = new Map<string, string>();
    sources.forEach((type) =>
      Object.entries(type.attrSchema?.properties ?? {}).forEach(([key, property]) =>
        seen.set(key, fieldLabel(key, property))
      )
    );
    return [...seen.entries()].sort().map(([value, label]) => ({ value, label }));
  }, [onDemands, props.demands, props.resourceTypes, selectedType]);

  /**
   * L'attribut choisi vit ici tant qu'aucune valeur ne l'accompagne.
   *
   * <p>Un scope ne sait pas exprimer « filtrer sur le statut, valeur encore
   * indéterminée » : `buildScope` rend alors `{}`. Sans cet état local, la liste
   * retomberait sur « Aucun filtre » sous les doigts de l'utilisateur entre le
   * choix de l'attribut et celui de la valeur.
   */
  const [pendingAttribute, setPendingAttribute] = React.useState<string | null>(null);

  const currentAttribute =
    shape.kind === "simple" ? (shape.attribute ?? pendingAttribute) : pendingAttribute;

  const property = React.useMemo(() => {
    if (onDemands || !currentAttribute) return undefined;
    const sources = selectedType ? [selectedType] : props.resourceTypes;
    for (const type of sources) {
      const found = type.attrSchema?.properties?.[currentAttribute];
      if (found) return found;
    }
    return undefined;
  }, [onDemands, currentAttribute, selectedType, props.resourceTypes]);

  /** Les valeurs déclarées par le schéma, ou à défaut celles réellement présentes. */
  const valueOptions: string[] = React.useMemo(() => {
    if (!currentAttribute) return [];
    if (property?.enum) return property.enum;

    const observed = new Set<string>();
    const collect = (attributes: Record<string, unknown>) => {
      const raw = attributes[currentAttribute];
      if (Array.isArray(raw)) raw.forEach((item) => observed.add(String(item)));
      else if (raw !== undefined && raw !== null) observed.add(String(raw));
    };
    if (onDemands) props.demands.forEach((demand) => collect(demand.attributes));
    else
      props.resources
        .filter((resource) => !selectedType || resource.typeCode === selectedType.code)
        .forEach((resource) => collect(resource.attributes));
    return [...observed].sort();
  }, [currentAttribute, property, onDemands, props.demands, props.resources, selectedType]);

  // L'opérateur découle du type de l'attribut : on ne teste pas l'appartenance à
  // un tableau avec « égal ». C'est le piège du §7.8, retiré des mains de l'utilisateur.
  const operator = property?.type === "array" ? "contains" : "eq";

  function update(patch: Partial<Extract<ScopeShape, { kind: "simple" }>>) {
    const base: Extract<ScopeShape, { kind: "simple" }> =
      shape.kind === "simple"
        ? { ...shape, attribute: currentAttribute }
        : { kind: "simple", typeCode: null, attribute: currentAttribute, operator: "eq", value: "" };
    props.onChange(buildScope({ ...base, ...patch, operator: patch.operator ?? operator }));
  }

  function chooseAttribute(next: string | null) {
    setPendingAttribute(next);
    update({ attribute: next, value: "" });
  }

  // ─── L'aperçu ────────────────────────────────────────────────────────────

  const serialized = JSON.stringify(props.value ?? {});

  React.useEffect(() => {
    let cancelled = false;
    const timer = window.setTimeout(() => {
      api
        .scopePreview(props.scenarioId, JSON.parse(serialized), props.target)
        .then((result) => {
          if (!cancelled) {
            setPreview(result);
            setPreviewError(undefined);
          }
        })
        .catch((cause) => {
          if (!cancelled) {
            setPreview(undefined);
            setPreviewError(cause instanceof Error ? cause.message : String(cause));
          }
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [serialized, props.scenarioId, props.target]);

  const noun = onDemands ? "demande" : "ressource";

  return (
    <div className="space-y-3">
      <p className="app-muted text-xs font-bold uppercase tracking-wide">S'applique à</p>

      {shape.kind === "raw" ? (
        <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
          Ce filtre utilise une combinaison que le formulaire ne sait pas représenter
          (<code>and</code>, <code>or</code>, <code>not</code> imbriqués, comparaison
          numérique…). Il reste modifiable en mode avancé, plus bas. Le formulaire ne
          l'écrasera pas.
        </div>
      ) : (
        <div className="grid gap-3 sm:grid-cols-3">
          {!onDemands ? (
            <label className="block">
              <span className="app-muted mb-1 block text-xs font-medium">Type de ressource</span>
              <select
                className="app-input py-2 text-sm"
                value={shape.kind === "simple" ? (shape.typeCode ?? "") : ""}
                onChange={(event) => update({ typeCode: event.target.value || null })}
              >
                <option value="">Tous les types</option>
                {typeOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          ) : null}

          <label className="block">
            <span className="app-muted mb-1 block text-xs font-medium">Filtrer sur</span>
            <select
              className="app-input py-2 text-sm"
              value={currentAttribute ?? ""}
              onChange={(event) => chooseAttribute(event.target.value || null)}
            >
              <option value="">Aucun filtre</option>
              {attributeOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="app-muted mb-1 block text-xs font-medium">
              {operator === "contains" ? "Contient" : "Est égal à"}
            </span>
            {valueOptions.length > 0 ? (
              <select
                className="app-input py-2 text-sm"
                disabled={!currentAttribute}
                value={shape.kind === "simple" ? shape.value : ""}
                onChange={(event) => update({ value: event.target.value })}
              >
                <option value="">—</option>
                {valueOptions.map((option) => (
                  <option key={option} value={option}>
                    {option}
                  </option>
                ))}
              </select>
            ) : (
              <input
                className="app-input py-2 text-sm"
                disabled={!currentAttribute}
                value={shape.kind === "simple" ? shape.value : ""}
                onChange={(event) => update({ value: event.target.value })}
              />
            )}
          </label>
        </div>
      )}

      <ScopePreviewLine preview={preview} error={previewError} noun={noun}
                        expanded={expanded} onToggle={() => setExpanded((v) => !v)} />
    </div>
  );
}

/**
 * Un scope qui ne vise personne ne lève aucune erreur — c'est le mode de
 * défaillance le plus silencieux du moteur. Cette ligne le rend visible avant
 * d'enregistrer, plutôt qu'après une résolution sans effet.
 */
function ScopePreviewLine(props: {
  preview: ScopePreview | undefined;
  error: string | undefined;
  noun: string;
  expanded: boolean;
  onToggle: () => void;
}) {
  if (props.error) {
    return <p className="text-sm text-red-600">Filtre invalide : {props.error}</p>;
  }
  if (!props.preview) {
    return <p className="app-muted text-sm">Évaluation…</p>;
  }

  const { matched, total, samples } = props.preview;

  if (matched === 0) {
    return (
      <div className="rounded-lg border-2 border-red-200 bg-red-50 p-3 text-sm text-red-800">
        <strong>Ce filtre ne vise aucune {props.noun}.</strong> La règle serait
        enregistrée mais n'aurait aucun effet, sans qu'aucune erreur ne le signale.
        Vérifier l'attribut choisi et sa valeur.
      </div>
    );
  }

  return (
    <div className="text-sm">
      <button type="button" className="app-accent font-semibold" onClick={props.onToggle}>
        → {matched} {props.noun}
        {matched > 1 ? "s" : ""} visée{matched > 1 ? "s" : ""} sur {total}
        <span className="app-muted ml-2 font-normal">{props.expanded ? "masquer" : "voir"}</span>
      </button>
      {props.expanded ? (
        <div className="mt-2 flex flex-wrap gap-1">
          {samples.map((match) => (
            <Chip key={match.id}>{match.label}</Chip>
          ))}
          {matched > samples.length ? (
            <span className="app-muted text-xs">… et {matched - samples.length} autre(s)</span>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
