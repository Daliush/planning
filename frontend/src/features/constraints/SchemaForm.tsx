import type { JsonSchema, SchemaProperty } from "../../api/types";
import { DurationInput } from "../../ui/DurationInput";
import { enumLabel, fieldLabel } from "../../utils/labels";

/**
 * Le formulaire des réglages d'une règle, **généré depuis le JSON Schema du
 * plugin**.
 *
 * <p>C'est ce qui donne au système sa propriété la plus utile : ajouter un plugin
 * demain fait apparaître son formulaire tout seul, sans toucher au frontend. Le
 * jour où ce composant connaîtrait le nom d'un plugin ou d'un paramètre, la
 * propriété serait perdue.
 */
export function SchemaForm(props: {
  schema: JsonSchema;
  value: Record<string, unknown>;
  onChange: (value: Record<string, unknown>) => void;
}) {
  const properties = Object.entries(props.schema.properties ?? {});

  if (properties.length === 0) {
    return (
      <p className="app-muted text-sm">Cette règle n'a aucun réglage : elle s'applique telle quelle.</p>
    );
  }

  function set(key: string, value: unknown) {
    props.onChange({ ...props.value, [key]: value });
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      {properties.map(([key, property]) => (
        <Field
          key={key}
          name={key}
          property={property}
          value={props.value[key]}
          onChange={(value) => set(key, value)}
        />
      ))}
    </div>
  );
}

function Field(props: {
  name: string;
  property: SchemaProperty;
  value: unknown;
  onChange: (value: unknown) => void;
}) {
  const { property } = props;
  const label = fieldLabel(props.name, property);
  // La description sert d'aide seulement si elle n'a pas déjà servi d'étiquette.
  const hint = property.title ? property.description : undefined;

  if (property.enum) {
    return (
      <label className="block">
        <span className="mb-1.5 block text-sm font-medium">{label}</span>
        <select
          className="app-input py-2 text-sm"
          value={String(props.value ?? "")}
          onChange={(event) => props.onChange(event.target.value)}
        >
          <option value="">—</option>
          {property.enum.map((option) => (
            <option key={option} value={option}>
              {enumLabel(option)}
            </option>
          ))}
        </select>
        {hint ? <span className="app-muted mt-1 block text-xs">{hint}</span> : null}
      </label>
    );
  }

  // Le marqueur d'unité est ce qui distingue une durée d'un simple compteur : sans
  // lui, les deux ont exactement le même schéma.
  if (property["x-unit"] === "minutes") {
    return (
      <DurationInput
        label={label}
        hint={hint}
        minimum={property.minimum}
        value={typeof props.value === "number" ? props.value : (property.minimum ?? 0)}
        onChange={props.onChange}
      />
    );
  }

  if (property["x-unit"] === "timeOfDay") {
    return (
      <label className="block">
        <span className="mb-1.5 block text-sm font-medium">{label}</span>
        <input
          className="app-input py-2 text-sm"
          type="time"
          value={String(props.value ?? "").slice(0, 5)}
          onChange={(event) => props.onChange(event.target.value)}
        />
        {hint ? <span className="app-muted mt-1 block text-xs">{hint}</span> : null}
      </label>
    );
  }

  if (property.type === "integer" || property.type === "number") {
    return (
      <label className="block">
        <span className="mb-1.5 block text-sm font-medium">{label}</span>
        <input
          className="app-input py-2 text-sm"
          type="number"
          min={property.minimum}
          value={typeof props.value === "number" ? props.value : ""}
          onChange={(event) => props.onChange(Number(event.target.value))}
        />
        {hint ? <span className="app-muted mt-1 block text-xs">{hint}</span> : null}
      </label>
    );
  }

  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium">{label}</span>
      <input
        className="app-input py-2 text-sm"
        value={String(props.value ?? "")}
        onChange={(event) => props.onChange(event.target.value)}
      />
      {hint ? <span className="app-muted mt-1 block text-xs">{hint}</span> : null}
    </label>
  );
}
