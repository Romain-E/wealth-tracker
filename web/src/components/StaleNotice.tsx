import { WarningIcon } from "./WarningIcon";

interface Props {
  stale: boolean;
  warnings: readonly string[];
}

/**
 * Says when the figures on screen are not fully live, instead of presenting them as if they were:
 * prices served from memory while the source is down, or positions valued at cost for lack of a
 * price. Renders nothing when everything is current.
 */
export function StaleNotice({ stale, warnings }: Props) {
  if (!stale && warnings.length === 0) {
    return null;
  }
  return (
    <div className="notice" role="status">
      <WarningIcon />
      <div>
        <p className="notice-title">
          {stale ? "Certains prix ne sont pas à jour" : "Certaines valeurs sont estimées"}
        </p>
        {stale && (
          <p>La source des cours ne répond pas : les derniers prix connus sont affichés.</p>
        )}
        {warnings.length > 0 && (
          <ul>
            {warnings.map((warning) => (
              <li key={warning}>{warning}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
