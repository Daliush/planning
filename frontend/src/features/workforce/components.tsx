import React from "react";

export type IconName =
  | "calendar"
  | "people"
  | "absence"
  | "settings"
  | "plus"
  | "back"
  | "chevron"
  | "close"
  | "check"
  | "clock"
  | "restaurant"
  | "company"
  | "school"
  | "spark"
  | "edit"
  | "trash"
  | "print"
  | "alert";
const paths: Record<IconName, React.ReactNode> = {
  calendar: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="3" />
      <path d="M7 3v4m10-4v4M3 11h18m-13 5h.01M12 16h.01M16 16h.01" />
    </>
  ),
  people: (
    <>
      <circle cx="9" cy="8" r="3" />
      <path d="M3 21v-3a6 6 0 0 1 12 0v3M17 5a3 3 0 0 1 0 6m1 4a5 5 0 0 1 3 5" />
    </>
  ),
  absence: (
    <>
      <rect x="3" y="5" width="18" height="16" rx="3" />
      <path d="M7 3v4m10-4v4M3 11h18m-11 4 4 4m0-4-4 4" />
    </>
  ),
  settings: (
    <>
      <path d="M4 7h16M4 17h16M8 4v6m8 4v6" />
      <circle cx="8" cy="7" r="2" />
      <circle cx="16" cy="17" r="2" />
    </>
  ),
  plus: <path d="M12 5v14M5 12h14" />,
  back: <path d="m13 5-7 7 7 7M6 12h15" />,
  chevron: <path d="m9 5 7 7-7 7" />,
  close: <path d="m6 6 12 12M18 6 6 18" />,
  check: <path d="m5 12 4 4L19 6" />,
  clock: (
    <>
      <circle cx="12" cy="12" r="9" />
      <path d="M12 7v5l3 2" />
    </>
  ),
  restaurant: (
    <>
      <path d="M5 3v6a3 3 0 0 0 6 0V3M8 3v18m12 0V3c-4 2-5 7-5 10h5" />
    </>
  ),
  company: (
    <>
      <rect x="3" y="7" width="18" height="14" rx="2" />
      <path d="M8 7V4h8v3M3 12h18m-11 0v3h2v-3" />
    </>
  ),
  school: (
    <>
      <path d="m2 9 10-5 10 5-10 5-10-5Zm4 3v6c4 3 8 3 12 0v-6m4-3v8" />
    </>
  ),
  spark: (
    <>
      <path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z" />
      <path d="M20 3v4m-2-2h4" />
    </>
  ),
  edit: (
    <>
      <path d="m15 4 5 5M4 20l5-1L21 7a2 2 0 0 0-4-4L5 15l-1 5Z" />
    </>
  ),
  trash: (
    <>
      <path d="M3 6h18M9 6V3h6v3M6 6l1 15h10l1-15M10 10v7m4-7v7" />
    </>
  ),
  print: (
    <>
      <path d="M6 9V3h12v6M6 17H3v-7h18v7h-3M6 14h12v7H6zM17 11h.01" />
    </>
  ),
  alert: (
    <>
      <path d="M12 3 2 21h20L12 3Zm0 6v5m0 3h.01" />
    </>
  ),
};
export function Icon({
  name,
  className = "",
}: {
  name: IconName;
  className?: string;
}) {
  return (
    <svg
      className={`wf-icon ${className}`}
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.7"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {paths[name]}
    </svg>
  );
}
export function WButton({
  children,
  icon,
  variant = "primary",
  className = "",
  ...props
}: React.ButtonHTMLAttributes<HTMLButtonElement> & {
  icon?: IconName;
  variant?: "primary" | "secondary" | "quiet" | "danger";
}) {
  return (
    <button
      type="button"
      className={`wf-button wf-button--${variant} ${className}`}
      {...props}
    >
      {icon && <Icon name={icon} />}
      <span>{children}</span>
    </button>
  );
}
export function Avatar({
  label,
  index = 0,
}: {
  label: string;
  index?: number;
}) {
  return (
    <span className={`wf-avatar wf-avatar--${index % 5}`} aria-hidden="true">
      {label
        .trim()
        .split(/\s+/)
        .map((part) => part[0])
        .slice(0, 2)
        .join("")
        .toUpperCase()}
    </span>
  );
}
export function Status({
  children,
  tone = "neutral",
}: {
  children: React.ReactNode;
  tone?: "neutral" | "good" | "warning";
}) {
  return <span className={`wf-status wf-status--${tone}`}>{children}</span>;
}
export function ErrorNotice({
  message,
  retry,
}: {
  message: string;
  retry?: () => void;
}) {
  return (
    <div className="wf-notice wf-notice--error" role="alert">
      <Icon name="alert" />
      <div>
        <p>{message}</p>
        {retry && (
          <button className="wf-text-button" onClick={retry}>
            Réessayer
          </button>
        )}
      </div>
    </div>
  );
}
export function EmptyPanel({
  icon = "calendar",
  title,
  description,
  action,
}: {
  icon?: IconName;
  title: string;
  description: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="wf-empty">
      <span className="wf-empty-symbol">
        <Icon name={icon} />
      </span>
      <h2>{title}</h2>
      <p>{description}</p>
      {action}
    </div>
  );
}
export function EditorDialog({
  title,
  description,
  onClose,
  children,
}: {
  title: string;
  description?: string;
  onClose: () => void;
  children: React.ReactNode;
}) {
  const ref = React.useRef<HTMLDialogElement>(null);
  React.useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    ref.current?.showModal();
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = oldOverflow;
      previous?.focus();
    };
  }, []);
  const titleId = React.useId();
  return (
    <dialog
      ref={ref}
      className="wf-dialog"
      aria-labelledby={titleId}
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <header>
        <div>
          <h2 id={titleId}>{title}</h2>
          {description && <p>{description}</p>}
        </div>
        <button
          type="button"
          className="wf-icon-button"
          aria-label="Fermer"
          onClick={onClose}
        >
          <Icon name="close" />
        </button>
      </header>
      {children}
    </dialog>
  );
}
export function FormField({
  label,
  hint,
  children,
}: {
  label: string;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <label className="wf-field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
