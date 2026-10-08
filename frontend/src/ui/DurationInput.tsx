import { splitMinutes } from "../utils/labels";

/**
 * Saisie d'une durée en heures et minutes.
 *
 * <p>Le modèle stocke des minutes — la grille est un paramètre modifiable, un
 * seuil exprimé en créneaux serait corrompu par un changement de granularité. Mais
 * personne ne pense en minutes : la conversion appartient à l'interface.
 */
export function DurationInput(props: {
  label: string;
  value: number;
  onChange: (minutes: number) => void;
  hint?: string;
  minimum?: number;
}) {
  const { hours, minutes } = splitMinutes(Math.max(props.value ?? 0, 0));

  function update(nextHours: number, nextMinutes: number) {
    const total = Math.max(nextHours, 0) * 60 + Math.max(nextMinutes, 0);
    props.onChange(Math.max(total, props.minimum ?? 0));
  }

  return (
    <div className="block">
      <span className="mb-1.5 block text-sm font-medium">{props.label}</span>
      <div className="flex items-center gap-2">
        <input
          className="app-input w-20 py-2 text-sm"
          type="number"
          min={0}
          value={hours}
          onChange={(event) => update(Number(event.target.value), minutes)}
        />
        <span className="app-muted text-sm">h</span>
        <input
          className="app-input w-20 py-2 text-sm"
          type="number"
          min={0}
          max={59}
          step={5}
          value={minutes}
          onChange={(event) => update(hours, Number(event.target.value))}
        />
        <span className="app-muted text-sm">min</span>
      </div>
      {props.hint ? <span className="app-muted mt-1 block text-xs">{props.hint}</span> : null}
    </div>
  );
}
