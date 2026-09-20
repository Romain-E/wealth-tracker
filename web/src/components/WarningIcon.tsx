/** The warning status icon: yellow triangle, ink exclamation mark. Always used beside a label. */
export function WarningIcon({ label }: { label?: string }) {
  return (
    <svg
      className="status-icon"
      viewBox="0 0 20 20"
      role={label ? "img" : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
    >
      <path className="warning-fill" d="M10 1.5 19 18H1z" />
      <path className="warning-glyph" d="M9 7h2v5.5H9zm0 7h2v2H9z" />
    </svg>
  );
}
