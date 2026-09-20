import type { ReactNode } from "react";

interface Props {
  label: string;
  value: ReactNode;
  note?: ReactNode;
}

/** One headline figure. Rendered inside a `<dl className="tiles">`. */
export function StatTile({ label, value, note }: Props) {
  return (
    <div className="tile">
      <dt>{label}</dt>
      <dd className="tile-value">{value}</dd>
      {note !== undefined && <dd className="tile-note">{note}</dd>}
    </div>
  );
}
