import { usePortfolio } from "../api/hooks";
import { AccountsTable } from "../components/AccountsTable";
import { AllocationBars } from "../components/AllocationBars";
import { PortfolioPerformance } from "../components/PerformanceSection";
import { QueryBoundary } from "../components/QueryBoundary";
import { StaleNotice } from "../components/StaleNotice";
import { formatDate, formatMoney } from "../format";
import { usePageTitle } from "../usePageTitle";

export function DashboardPage() {
  usePageTitle("Tableau de bord");
  const portfolio = usePortfolio();

  return (
    <>
      <QueryBoundary query={portfolio}>
        {(value) => (
          <>
            <header className="page-head">
              <h1 className="eyebrow">Patrimoine total</h1>
              <p className="hero">{formatMoney(value.total.amount, value.total.currency)}</p>
              <p className="secondary">Valorisé le {formatDate(value.asOf)}</p>
            </header>
            <StaleNotice stale={value.stale} warnings={value.warnings} />
            <div className="grid-2">
              <AllocationBars
                categories={value.byCategory}
                slices={value.byType}
                total={value.total}
              />
              <AccountsTable
                accounts={value.accounts}
                categories={value.byCategory}
                types={value.byType}
              />
            </div>
          </>
        )}
      </QueryBoundary>
      <PortfolioPerformance />
    </>
  );
}
