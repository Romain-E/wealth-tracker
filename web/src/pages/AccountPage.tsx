import { Link, useParams } from "react-router";
import { useAccount } from "../api/hooks";
import { AllowanceMeter } from "../components/AllowanceMeter";
import { MovementForm } from "../components/MovementForm";
import { AccountPerformance } from "../components/PerformanceSection";
import { PositionsTable } from "../components/PositionsTable";
import { QueryBoundary } from "../components/QueryBoundary";
import { StaleNotice } from "../components/StaleNotice";
import { StatTile } from "../components/StatTile";
import { formatDate, formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";

export function AccountPage() {
  const { accountId = "" } = useParams();
  const account = useAccount(accountId);
  usePageTitle(account.data?.label);

  return (
    <>
      <nav aria-label="Fil d’Ariane">
        <Link to="/">← Tableau de bord</Link>
      </nav>
      <QueryBoundary query={account}>
        {(detail) => (
          <>
            <header className="page-head">
              <p className="eyebrow">{detail.typeLabel}</p>
              <h1>{detail.label}</h1>
              <p className="secondary">Ouvert le {formatDate(detail.openedOn)}</p>
            </header>
            <StaleNotice stale={detail.stale} warnings={detail.warnings} />
            <dl className="tiles">
              <StatTile
                label="Valeur"
                value={formatMoney(detail.totalValue.amount, detail.totalValue.currency)}
              />
              <StatTile
                label="Liquidités"
                value={formatMoney(detail.cashBalance.amount, detail.cashBalance.currency)}
              />
            </dl>
            {detail.paymentCeiling && detail.remainingAllowance ? (
              <AllowanceMeter
                ceiling={detail.paymentCeiling}
                remaining={detail.remainingAllowance}
              />
            ) : (
              <p className="secondary">Aucun plafond de versement pour cette enveloppe.</p>
            )}
            {detail.positions.length > 0 && <PositionsTable positions={detail.positions} />}
            <AccountPerformance accountId={detail.id} />
            <MovementForm accountId={detail.id} currency={detail.currency} />
          </>
        )}
      </QueryBoundary>
    </>
  );
}
