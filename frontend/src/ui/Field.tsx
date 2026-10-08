import React from "react";

export function Field(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  type?: "text" | "number" | "time";
  hint?: string;
  className?: string;
}) {
  return (
    <label className={`block ${props.className ?? ""}`}>
      <span className="mb-1.5 block text-sm font-medium">{props.label}</span>
      <input
        type={props.type ?? "text"}
        value={props.value}
        placeholder={props.placeholder}
        onChange={(event) => props.onChange(event.target.value)}
        className="app-input"
      />
      {props.hint ? <span className="app-muted mt-1 block text-xs">{props.hint}</span> : null}
    </label>
  );
}

export function SelectField(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  options: { value: string; label: string }[];
  className?: string;
}) {
  return (
    <label className={`block ${props.className ?? ""}`}>
      <span className="mb-1.5 block text-sm font-medium">{props.label}</span>
      <select
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
        className="app-input"
      >
        {props.options.map((option) => (
          <option key={option.value} value={option.value}>
            {option.label}
          </option>
        ))}
      </select>
    </label>
  );
}

/**
 * Les scopes, critères et paramètres sont du JSON : le laisser éditer tel quel
 * est fidèle au modèle et évite un générateur de formulaire à moitié capable.
 */
export function JsonField(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  hint?: string;
  rows?: number;
}) {
  const invalid = React.useMemo(() => {
    try {
      JSON.parse(props.value || "{}");
      return false;
    } catch {
      return true;
    }
  }, [props.value]);

  return (
    <label className="block">
      <span className="mb-1.5 block text-sm font-medium">{props.label}</span>
      <textarea
        value={props.value}
        rows={props.rows ?? 3}
        spellCheck={false}
        onChange={(event) => props.onChange(event.target.value)}
        className="app-input font-mono text-xs"
        style={invalid ? { borderColor: "#dc2626" } : undefined}
      />
      {invalid ? (
        <span className="mt-1 block text-xs text-red-600">JSON invalide</span>
      ) : props.hint ? (
        <span className="app-muted mt-1 block text-xs">{props.hint}</span>
      ) : null}
    </label>
  );
}
