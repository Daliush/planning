import React from "react";
import { PlanningApiError } from "../api/client";

type AsyncState<T> = {
  data: T | undefined;
  loading: boolean;
  error: string | undefined;
};

export function describeError(error: unknown): string {
  if (error instanceof PlanningApiError) return `${error.message} (${error.code})`;
  if (error instanceof Error) return error.message;
  return String(error);
}

/** Chargement simple avec rechargement explicite. Pas de cache : l'écran est court. */
export function useAsync<T>(load: () => Promise<T>, deps: React.DependencyList) {
  const [state, setState] = React.useState<AsyncState<T>>({
    data: undefined,
    loading: true,
    error: undefined,
  });
  const [nonce, setNonce] = React.useState(0);

  React.useEffect(() => {
    let cancelled = false;
    setState((current) => ({ ...current, loading: true, error: undefined }));

    load()
      .then((data) => {
        if (!cancelled) setState({ data, loading: false, error: undefined });
      })
      .catch((error) => {
        if (!cancelled) setState({ data: undefined, loading: false, error: describeError(error) });
      });

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  const reload = React.useCallback(() => setNonce((value) => value + 1), []);
  return { ...state, reload };
}
