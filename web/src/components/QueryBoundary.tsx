import type { UseQueryResult } from "@tanstack/react-query";
import type { ReactNode } from "react";
import { ErrorPanel } from "./ErrorPanel";

interface Props<T> {
  query: UseQueryResult<T>;
  children: (data: T) => ReactNode;
}

/** Renders a query's data, or says plainly that it is loading or why it failed. */
export function QueryBoundary<T>({ query, children }: Props<T>) {
  if (query.isPending) {
    return (
      <p className="loading" role="status">
        Chargement…
      </p>
    );
  }
  if (query.isError) {
    return <ErrorPanel error={query.error} />;
  }
  return <>{children(query.data)}</>;
}
