import { Link } from "react-router";
import type { AccountSummary, CategorySlice, TypeSlice } from "../api/client";
import { formatMoney, formatPercent } from "../format";
import { WarningIcon } from "./WarningIcon";

interface Props {
  accounts: readonly AccountSummary[];
  categories: readonly CategorySlice[];
  /** The envelope types in the domain's order, which rows within a family follow. */
  types: readonly TypeSlice[];
}

/**
 * Every account, with its value and share of the total, grouped by family: passbooks apart from
 * investments. Each family is a row group of the same table, so the columns stay aligned across
 * groups and a screen reader announces the family with each account. Each account leads to its page.
 *
 * Within a family, rows follow envelope order, as the allocation chart does, then the order the API
 * sends (oldest account first): the sort is stable.
 */
export function AccountsTable({ accounts, categories, types }: Props) {
  const rank = (account: AccountSummary) => types.findIndex((type) => type.type === account.type);
  const ordered = accounts.toSorted((a, b) => rank(a) - rank(b));

  return (
    <div className="card table-wrap">
      <table>
        <caption>Comptes</caption>
        <thead>
          <tr>
            <th scope="col">Compte</th>
            <th scope="col" className="hide-narrow">
              Enveloppe
            </th>
            <th scope="col" className="num">
              Valeur
            </th>
            <th scope="col" className="num">
              Part
            </th>
          </tr>
        </thead>
        {categories.map((category) => (
          <tbody key={category.category}>
            <tr className="group-row">
              <th scope="rowgroup">{category.label}</th>
              <td className="hide-narrow" />
              <td className="num">{formatMoney(category.value.amount, category.value.currency)}</td>
              <td className="num">{formatPercent(category.sharePercent)}</td>
            </tr>
            {ordered
              .filter((account) => account.category === category.category)
              .map((account) => (
                <tr key={account.id}>
                  <th scope="row">
                    <Link to={`/comptes/${account.id}`}>{account.label}</Link>
                  </th>
                  <td className="hide-narrow">{account.typeLabel}</td>
                  <td className="num">
                    <span className="signed">
                      {(account.stale || account.warnings.length > 0) && (
                        <WarningIcon label="Valeur estimée ou prix non à jour" />
                      )}
                      {formatMoney(account.value.amount, account.value.currency)}
                    </span>
                  </td>
                  <td className="num">{formatPercent(account.sharePercent)}</td>
                </tr>
              ))}
          </tbody>
        ))}
      </table>
    </div>
  );
}
