import type { Position } from "../api/client";
import {
  formatDateTime,
  formatMoney,
  formatPrice,
  formatQuantity,
  formatSignedMoney,
} from "../format";
import { Signed } from "./Signed";
import { WarningIcon } from "./WarningIcon";

const KIND_LABELS: Record<string, string> = {
  EQUITY: "Action",
  ETF: "ETF",
  FUND: "Fonds",
  CRYPTO: "Crypto",
};

/**
 * Holdings with what they cost and what they are worth. An instrument without a price shows its
 * cost as its value and says so, rather than showing a loss that did not happen.
 */
export function PositionsTable({ positions }: { positions: readonly Position[] }) {
  return (
    <div className="card table-wrap">
      <table>
        <caption>Positions</caption>
        <thead>
          <tr>
            <th scope="col">Instrument</th>
            <th scope="col">Type</th>
            <th scope="col" className="num">
              Quantité
            </th>
            <th scope="col" className="num">
              Prix de revient
            </th>
            <th scope="col" className="num">
              Cours
            </th>
            <th scope="col" className="num">
              Valeur
            </th>
            <th scope="col" className="num">
              Plus-value latente
            </th>
          </tr>
        </thead>
        <tbody>
          {positions.map((position) => {
            const currency = position.marketValue.currency;
            return (
              <tr key={position.instrument}>
                <th scope="row">{position.instrument}</th>
                <td>{KIND_LABELS[position.kind] ?? position.kind}</td>
                <td className="num">{formatQuantity(position.quantity)}</td>
                <td className="num">{formatPrice(position.averageCost, currency)}</td>
                <td className="num">
                  {position.currentPrice === null ? (
                    <span className="signed">
                      <WarningIcon />
                      non coté
                    </span>
                  ) : (
                    <span
                      className="signed"
                      title={
                        position.pricedAt
                          ? `Cours du ${formatDateTime(position.pricedAt)}`
                          : undefined
                      }
                    >
                      {position.stale && <WarningIcon label="Cours non à jour" />}
                      {formatPrice(position.currentPrice, currency)}
                    </span>
                  )}
                </td>
                <td className="num">
                  {formatMoney(position.marketValue.amount, position.marketValue.currency)}
                </td>
                <td className="num">
                  <Signed value={position.unrealisedGain.amount}>
                    {formatSignedMoney(
                      position.unrealisedGain.amount,
                      position.unrealisedGain.currency,
                    )}
                  </Signed>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
