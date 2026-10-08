import React from "react";
import { createPortal } from "react-dom";

const FOCUSABLE =
  'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Modale d'édition.
 *
 * <p>Portée dans `document.body` : un éditeur imbriqué dans une carte hériterait
 * de son `overflow` et de son contexte d'empilement, et se retrouverait rogné ou
 * passerait sous un autre élément.
 *
 * <p>Le corps défile seul, l'en-tête et le pied restent visibles : les
 * formulaires de règle sont longs, et le bouton d'enregistrement ne doit jamais
 * sortir de l'écran.
 */
export function Modal(props: {
  title: string;
  description?: string;
  onClose: () => void;
  footer?: React.ReactNode;
  width?: "md" | "lg";
  children: React.ReactNode;
}) {
  const dialogRef = React.useRef<HTMLDivElement>(null);

  // Rendre le focus à l'élément qui a ouvert la modale : sans ça, la navigation
  // au clavier repart du début de la page à chaque fermeture.
  React.useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    dialogRef.current?.focus();
    return () => previous?.focus?.();
  }, []);

  React.useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = previous;
    };
  }, []);

  function onKeyDown(event: React.KeyboardEvent) {
    if (event.key === "Escape") {
      event.stopPropagation();
      props.onClose();
      return;
    }
    if (event.key !== "Tab" || !dialogRef.current) return;

    // Piège de focus : sans lui, la tabulation part derrière la modale, sur des
    // contrôles que le fond rend pourtant inaccessibles à la souris.
    const focusables = [...dialogRef.current.querySelectorAll<HTMLElement>(FOCUSABLE)];
    if (focusables.length === 0) return;
    const first = focusables[0];
    const last = focusables[focusables.length - 1];

    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto p-3 sm:p-6"
      style={{ background: "rgba(15, 23, 42, 0.45)" }}
      onMouseDown={(event) => {
        // Uniquement sur le fond : un glissé de sélection terminé hors du panneau
        // ne doit pas fermer la modale.
        if (event.target === event.currentTarget) props.onClose();
      }}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-label={props.title}
        tabIndex={-1}
        onKeyDown={onKeyDown}
        className={[
          "app-surface my-auto flex max-h-[92vh] w-full flex-col rounded-xl border shadow-2xl outline-none",
          props.width === "lg" ? "max-w-4xl" : "max-w-2xl",
        ].join(" ")}
      >
        <header className="app-divider flex items-start justify-between gap-4 border-b p-4 sm:p-5">
          <div className="min-w-0">
            <h2 className="app-accent text-lg font-bold">{props.title}</h2>
            {props.description ? (
              <p className="app-muted mt-1 text-sm">{props.description}</p>
            ) : null}
          </div>
          <button
            type="button"
            onClick={props.onClose}
            aria-label="Fermer"
            className="app-muted shrink-0 rounded-lg px-2 py-1 text-xl leading-none hover:opacity-70"
          >
            ×
          </button>
        </header>

        <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5">{props.children}</div>

        {props.footer ? (
          <footer className="app-divider flex flex-wrap justify-end gap-2 border-t p-4 sm:p-5">
            {props.footer}
          </footer>
        ) : null}
      </div>
    </div>,
    document.body
  );
}
