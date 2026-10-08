import React from "react";

export function Section(props: {
  title: string;
  description?: string;
  right?: React.ReactNode;
  children: React.ReactNode;
}) {
  return (
    <section className="app-surface rounded-xl border p-4 shadow-sm sm:p-6">
      <header className="mb-4 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <h2 className="text-lg font-semibold">{props.title}</h2>
          {props.description ? (
            <p className="app-muted mt-1 text-sm">{props.description}</p>
          ) : null}
        </div>
        {props.right}
      </header>
      {props.children}
    </section>
  );
}

export function Card(props: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`app-surface-muted rounded-lg border p-3 ${props.className ?? ""}`}>
      {props.children}
    </div>
  );
}

export function Chip({ children, tone }: { children: React.ReactNode; tone?: "accent" | "danger" }) {
  const style =
    tone === "accent"
      ? { borderColor: "var(--app-accent)", color: "var(--app-accent)" }
      : tone === "danger"
        ? { borderColor: "#dc2626", color: "#dc2626" }
        : undefined;
  return (
    <span className="app-chip" style={style}>
      {children}
    </span>
  );
}
