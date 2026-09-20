import type { UseQueryResult } from "@tanstack/react-query";
import { useId } from "react";
import type { Performance } from "../api/client";
import { useAccountPerformance, usePortfolioPerformance } from "../api/hooks";
import { formatMoney, formatPercent, formatSignedMoney } from "../format";
import { useChartWindow, type WindowId } from "./chartWindow";
import { QueryBoundary } from "./QueryBoundary";
import { Signed } from "./Signed";
import { StatTile } from "./StatTile";
import { ValueChart } from "./ValueChart";
import { WindowPicker } from "./WindowPicker";

export function PortfolioPerformance() {
  const [windowId, window, select] = useChartWindow();
  return (
    <PerformanceView
      query={usePortfolioPerformance(window)}
      windowId={windowId}
      onWindowChange={select}
      scope="de l’ensemble des comptes"
    />
  );
}

export function AccountPerformance({ accountId }: { accountId: string }) {
  const [windowId, window, select] = useChartWindow();
  return (
    <PerformanceView
      query={useAccountPerformance(accountId, window)}
      windowId={windowId}
      onWindowChange={select}
      scope="du compte"
    />
  );
}

interface ViewProps {
  query: UseQueryResult<Performance>;
  windowId: WindowId;
  onWindowChange: (id: WindowId) => void;
  scope: string;
}

/**
 * The headline figures cover the whole history, while the period only applies to the curve. The
 * page says so, because a reader who picks "1 an" would otherwise read the gain as a one-year gain.
 */
function PerformanceView({ query, windowId, onWindowChange, scope }: ViewProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId}>Performance</h2>
      <QueryBoundary query={query}>
        {(performance) => (
          <>
            <dl className="tiles">
              <StatTile
                label="Versé net"
                value={formatMoney(performance.netInvested.amount, performance.currency)}
                note="Dépôts moins retraits"
              />
              <StatTile
                label="Valeur actuelle"
                value={formatMoney(performance.currentValue.amount, performance.currency)}
              />
              <StatTile
                label="Gain"
                value={
                  <Signed value={performance.netGain.amount}>
                    {formatSignedMoney(performance.netGain.amount, performance.currency)}
                  </Signed>
                }
                note={`${formatPercent(performance.netGainPercent, { signed: true })} du versé`}
              />
              <StatTile
                label="Rendement annualisé"
                value={
                  performance.annualisedReturnPercent === null ? (
                    "—"
                  ) : (
                    <Signed value={performance.annualisedReturnPercent}>
                      {formatPercent(performance.annualisedReturnPercent, { signed: true })}
                    </Signed>
                  )
                }
                note={
                  performance.annualisedReturnPercent === null
                    ? "Pas assez d’historique pour l’annualiser"
                    : "Taux de rendement interne, par an"
                }
              />
            </dl>
            <p className="section-note">
              Chiffres calculés depuis l’ouverture {scope}. La période ne s’applique qu’à la courbe.
            </p>
            <WindowPicker value={windowId} onChange={onWindowChange} />
            <div className="card">
              <ValueChart
                title="Valeur au fil du temps"
                points={performance.series}
                currency={performance.currency}
                dimmed={query.isPlaceholderData}
              />
            </div>
          </>
        )}
      </QueryBoundary>
    </section>
  );
}
