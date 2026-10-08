export function Loading({ label = "Chargement…" }: { label?: string }) {
  return <div className="app-surface app-muted rounded-xl border p-6 text-sm">{label}</div>;
}

export function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="rounded-xl border-2 border-red-200 bg-red-50 p-4 text-sm text-red-800">
      {message}
    </div>
  );
}

export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="app-surface app-divider rounded-xl border border-dashed p-8 text-center">
      <p className="font-medium">{title}</p>
      {hint ? <p className="app-muted mt-1 text-sm">{hint}</p> : null}
    </div>
  );
}
