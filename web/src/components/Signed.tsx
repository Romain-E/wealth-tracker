interface Props {
  value: number;
  /** The formatted value, sign included: the text itself says up or down. */
  children: string;
}

/**
 * A gain or a loss. The number stays in ink, with its sign; the direction is repeated by an arrow
 * in the status colours. Colour is never the only carrier: the sign is there for anyone who cannot
 * tell the green from the red, and red text on a dark background would miss the contrast bar.
 */
export function Signed({ value, children }: Props) {
  return (
    <span className="signed">
      {value !== 0 && (
        <svg
          className={value > 0 ? "signed-icon up" : "signed-icon down"}
          viewBox="0 0 10 10"
          aria-hidden="true"
        >
          <path d={value > 0 ? "M5 1 9.5 9H.5z" : "M5 9 .5 1h9z"} />
        </svg>
      )}
      <span>{children}</span>
    </span>
  );
}
