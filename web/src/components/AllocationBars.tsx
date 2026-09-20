import type { CategorySlice, Money, TypeSlice } from "../api/client";
import { formatMoney, formatPercent } from "../format";

interface Props {
  categories: readonly CategorySlice[];
  slices: readonly TypeSlice[];
  total: Money;
}

/**
 * How wealth splits across envelope types, grouped by family (passbooks, investments, property),
 * each family headed by its own total and share. The envelopes are names, not an order, so every
 * bar wears the same series colour: colouring them by size would only repeat what their length
 * says. Bars are scaled to the largest one across all families, so lengths compare between groups,
 * and each carries its value at the tip.
 *
 * The order is the one the API sends (the domain's own order), so it does not reshuffle when a
 * price moves.
 */
export function AllocationBars({ categories, slices, total }: Props) {
  const largest = Math.max(0, ...slices.map((slice) => slice.value.amount));

  return (
    <figure className="chart card">
      <figcaption>Répartition par enveloppe</figcaption>
      {categories.length === 0 ? (
        <p className="empty">Aucun compte pour l’instant.</p>
      ) : (
        <div className="bar-groups">
          {categories.map((category) => {
            const amount = formatMoney(category.value.amount, category.value.currency);
            const share = formatPercent(category.sharePercent);
            return (
              <section key={category.category} className="bar-group">
                <p className="bar-group-head" aria-hidden="true">
                  <span>{category.label}</span>
                  <span className="num">
                    {amount} · {share}
                  </span>
                </p>
                <ul className="bars" aria-label={`${category.label} : ${amount}, soit ${share}`}>
                  {slices
                    .filter((slice) => slice.category === category.category)
                    .map((slice) => (
                      <Bar key={slice.type} slice={slice} total={total} largest={largest} />
                    ))}
                </ul>
              </section>
            );
          })}
        </div>
      )}
    </figure>
  );
}

function Bar({ slice, total, largest }: { slice: TypeSlice; total: Money; largest: number }) {
  const amount = formatMoney(slice.value.amount, slice.value.currency);
  const share = formatPercent(total.amount > 0 ? (slice.value.amount / total.amount) * 100 : 0);
  const ratio = largest > 0 ? slice.value.amount / largest : 0;
  return (
    <li
      className="bar-row"
      tabIndex={0}
      aria-label={`${slice.label} : ${amount}, soit ${share} du total`}
    >
      <span className="bar-label" aria-hidden="true">
        {slice.label}
      </span>
      <span className="bar-track" aria-hidden="true">
        {/* The value at the tip needs room: the longest bar leaves 7.5rem for it. */}
        <span className="bar" style={{ width: `calc((100% - 7.5rem) * ${ratio})` }} />
        <span className="bar-value">{amount}</span>
      </span>
      <span className="tooltip" aria-hidden="true">
        <strong>{share}</strong>
        <span>{slice.label}</span>
      </span>
    </li>
  );
}
