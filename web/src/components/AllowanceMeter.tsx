import type { Money } from "../api/client";
import { formatMoney } from "../format";

interface Props {
  ceiling: Money;
  remaining: Money;
}

/**
 * How much of a regulatory ceiling has been paid in. A full meter is not an alarm: it is the
 * normal state of a well-used Livret A, so it stays in the accent colour and says what it means.
 */
export function AllowanceMeter({ ceiling, remaining }: Props) {
  const used = Math.max(ceiling.amount - remaining.amount, 0);
  const ratio = ceiling.amount > 0 ? Math.min(used / ceiling.amount, 1) : 0;
  const usedText = formatMoney(used, ceiling.currency);
  const ceilingText = formatMoney(ceiling.amount, ceiling.currency);

  return (
    <div className="card meter-block">
      <div className="meter-head">
        <span id="allowance-label">Plafond des versements</span>
        <span>{ceilingText}</span>
      </div>
      <div
        className="meter"
        role="meter"
        aria-labelledby="allowance-label"
        aria-valuemin={0}
        aria-valuemax={ceiling.amount}
        aria-valuenow={used}
        aria-valuetext={`${usedText} versés sur ${ceilingText}`}
      >
        <div className="meter-fill" style={{ width: `${ratio * 100}%` }} />
      </div>
      <p className="meter-caption">
        {usedText} versés ·{" "}
        {remaining.amount > 0
          ? `encore ${formatMoney(remaining.amount, remaining.currency)} autorisés`
          : "plafond atteint : les intérêts continuent de s’ajouter, les versements non"}
      </p>
    </div>
  );
}
