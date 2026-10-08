import React from "react";

export function PageShell(props: {
  title: string;
  subtitle?: string;
  right?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <div className="app-shell app-shell--magenta min-h-screen w-full px-3 py-4 sm:p-6">
      <div className="mx-auto w-full max-w-7xl space-y-4 sm:space-y-6">
        <header
          className="app-surface flex flex-col items-stretch justify-between gap-4 rounded-xl border p-4 shadow-sm sm:flex-row sm:items-start sm:p-6"
          style={{ borderColor: "var(--app-accent-soft)" }}
        >
          <div>
            <h1 className="app-accent text-2xl font-bold sm:text-3xl">{props.title}</h1>
            {props.subtitle ? <p className="app-muted mt-1">{props.subtitle}</p> : null}
          </div>
          {props.right}
        </header>
        {props.children}
      </div>
    </div>
  );
}
