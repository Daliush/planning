import React from "react";
import type { AttributeDefinition, JsonSchema } from "../../api/types";
import { DurationInput } from "../../ui/DurationInput";
import { JsonField } from "../../ui/Field";
import { Chip } from "../../ui/Section";
import { prettyJson } from "../../utils/format";
import { attributeFields, parseList, pruneEmpty, type AttributeField } from "./attributeFields";

/**
 * Les attributs métier d'une ressource, saisis comme un formulaire.
 *
 * <p>Le modèle reste un jsonb libre — c'est ce qui permet à une verticale
 * d'inventer ses propres clés sans migration. Mais un utilisateur métier n'a pas
 * à écrire du JSON pour dire qu'un enseignant fait des maths : les clés que le
 * produit connaît viennent du catalogue, et le formulaire les propose.
 *
 * <p>L'éditeur JSON reste accessible, replié. Il n'est pas un repli de secours :
 * c'est le seul moyen de poser une clé hors catalogue — un attribut d'entreprise
 * sur une école, une structure imbriquée — et il ne doit jamais disparaître.
 */
export function AttributesForm(props: {
  definitions: AttributeDefinition[];
  schema: JsonSchema | undefined;
  resourceTypeCode: string | undefined;
  value: Record<string, unknown>;
  onChange: (attributes: Record<string, unknown>) => void;
}) {
  const [advanced, setAdvanced] = React.useState(false);

  const fields = React.useMemo(
    () => attributeFields(props.definitions, props.schema, props.value, props.resourceTypeCode),
    [props.definitions, props.schema, props.value, props.resourceTypeCode]
  );

  function set(key: string, next: unknown) {
    props.onChange(pruneEmpty({ ...props.value, [key]: next }));
  }

  return (
    <div className="space-y-3">
      {fields.length === 0 ? (
        <p className="app-muted text-sm">
          Aucun attribut connu pour ce type de ressource. L'éditeur avancé permet
          d'en poser librement.
        </p>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2">
          {fields.map((field) => (
            <AttributeInput
              key={field.key}
              field={field}
              value={props.value[field.key]}
              onChange={(next) => set(field.key, next)}
            />
          ))}
        </div>
      )}

      <div>
        <button
          type="button"
          className="app-muted text-xs font-semibold underline"
          onClick={() => setAdvanced((current) => !current)}
        >
          {advanced ? "Masquer" : "Afficher"} l'éditeur JSON
        </button>

        {advanced ? (
          <div className="mt-2">
            <AdvancedJson value={props.value} onChange={props.onChange} />
          </div>
        ) : null}
      </div>
    </div>
  );
}

function AttributeInput(props: {
  field: AttributeField;
  value: unknown;
  onChange: (next: unknown) => void;
}) {
  const { field } = props;

  const label = (
    <span className="mb-1.5 flex flex-wrap items-center gap-2 text-sm font-medium">
      {field.title}
      {field.source === "value" ? <Chip>hors catalogue</Chip> : null}
      {field.readBy.length > 0 ? <Chip tone="accent">lu par {field.readBy.join(", ")}</Chip> : null}
    </span>
  );

  const hint = field.description ? (
    <span className="app-muted mt-1 block text-xs">{field.description}</span>
  ) : null;

  if (field.kind === "DURATION_MINUTES") {
    return (
      <div>
        <DurationInput
          label={field.title}
          value={typeof props.value === "number" ? props.value : 0}
          onChange={props.onChange}
          minimum={field.minimum ?? 0}
          hint={field.description}
        />
      </div>
    );
  }

  if (field.kind === "ENUM") {
    return (
      <label className="block">
        {label}
        <select
          className="app-input py-2 text-sm"
          value={typeof props.value === "string" ? props.value : ""}
          onChange={(event) => props.onChange(event.target.value)}
        >
          <option value="">—</option>
          {field.options.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        {hint}
      </label>
    );
  }

  if (field.kind === "TEXT_LIST") {
    const items = Array.isArray(props.value) ? props.value.map(String) : [];
    return (
      <label className="block">
        {label}
        <input
          className="app-input py-2 text-sm"
          value={items.join(", ")}
          placeholder="séparer par des virgules"
          onChange={(event) => props.onChange(parseList(event.target.value))}
        />
        {hint}
      </label>
    );
  }

  if (field.kind === "INTEGER") {
    return (
      <label className="block">
        {label}
        <input
          className="app-input py-2 text-sm"
          type="number"
          min={field.minimum ?? undefined}
          value={typeof props.value === "number" ? props.value : ""}
          onChange={(event) =>
            props.onChange(event.target.value === "" ? null : Number(event.target.value))
          }
        />
        {hint}
      </label>
    );
  }

  return (
    <label className="block">
      {label}
      <input
        className="app-input py-2 text-sm"
        value={typeof props.value === "string" ? props.value : ""}
        onChange={(event) => props.onChange(event.target.value)}
      />
      {hint}
    </label>
  );
}

/**
 * L'éditeur JSON, synchronisé dans les deux sens.
 *
 * <p>Le texte est un état local tant qu'il n'est pas analysable : sans cela,
 * chaque frappe qui casse momentanément le JSON — une accolade ouverte — serait
 * rejetée et le curseur sauterait. Le formulaire n'est prévenu qu'à partir d'un
 * document valide.
 */
function AdvancedJson(props: {
  value: Record<string, unknown>;
  onChange: (attributes: Record<string, unknown>) => void;
}) {
  const serialized = React.useMemo(() => prettyJson(props.value), [props.value]);
  const [draft, setDraft] = React.useState(serialized);
  const [dirty, setDirty] = React.useState(false);

  // Le formulaire a changé la valeur : reprendre la main, sauf si l'utilisateur
  // est en train de taper dans le textarea.
  React.useEffect(() => {
    if (!dirty) setDraft(serialized);
  }, [serialized, dirty]);

  function edit(next: string) {
    setDraft(next);
    setDirty(true);
    try {
      const parsed = JSON.parse(next.trim() === "" ? "{}" : next);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        props.onChange(parsed as Record<string, unknown>);
        setDirty(false);
      }
    } catch {
      /* document incomplet : on garde le brouillon, JsonField signale l'erreur */
    }
  }

  return (
    <JsonField
      label="JSON brut"
      value={draft}
      onChange={edit}
      rows={6}
      hint="Pour les clés hors catalogue. La clé « type » est ajoutée automatiquement au chargement."
    />
  );
}
