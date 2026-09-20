import type { AccountDetail, Performance, Portfolio } from "../api/client";

/*
 * Typed against the generated contract: if the API changes shape, these stop compiling, and so do
 * the tests that rely on them. A hand-written fixture that silently drifted from the real API would
 * let the tests pass against something the server no longer sends.
 */

export const LIVRET_A_ID = "00000000-0000-4000-a000-000000000001";
export const CTO_ID = "00000000-0000-4000-a000-000000000004";

const eur = (amount: number) => ({ amount, currency: "EUR" });

export const portfolio: Portfolio = {
  asOf: "2026-09-16",
  total: eur(434604.94),
  accounts: [
    {
      id: LIVRET_A_ID,
      type: "LIVRET_A",
      typeLabel: "Livret A",
      category: "SAVINGS",
      label: "Livret A",
      value: eur(23671.55),
      sharePercent: 5.4467,
      stale: false,
      warnings: [],
    },
    {
      id: CTO_ID,
      type: "CTO",
      typeLabel: "Compte-titres ordinaire",
      category: "INVESTMENTS",
      label: "Compte-titres",
      value: eur(12072.41),
      sharePercent: 2.7778,
      stale: false,
      warnings: [],
    },
  ],
  byType: [
    { type: "LIVRET_A", label: "Livret A", category: "SAVINGS", value: eur(23671.55) },
    {
      type: "CTO",
      label: "Compte-titres ordinaire",
      category: "INVESTMENTS",
      value: eur(12072.41),
    },
  ],
  byCategory: [
    {
      category: "SAVINGS",
      label: "Livrets d'épargne",
      value: eur(23671.55),
      sharePercent: 5.4467,
    },
    { category: "INVESTMENTS", label: "Placements", value: eur(12072.41), sharePercent: 2.7778 },
  ],
  stale: false,
  warnings: [],
};

export const livretA: AccountDetail = {
  id: LIVRET_A_ID,
  label: "Livret A",
  type: "LIVRET_A",
  typeLabel: "Livret A",
  currency: "EUR",
  openedOn: "2018-01-15",
  cashBalance: eur(23390.95),
  totalValue: eur(23671.55),
  paymentCeiling: eur(22950),
  remainingAllowance: eur(500),
  positions: [],
  stale: false,
  warnings: [],
};

export const compteTitres: AccountDetail = {
  id: CTO_ID,
  label: "Compte-titres",
  type: "CTO",
  typeLabel: "Compte-titres ordinaire",
  currency: "EUR",
  openedOn: "2023-02-01",
  cashBalance: eur(1986.01),
  totalValue: eur(12072.41),
  paymentCeiling: null,
  remainingAllowance: null,
  positions: [
    {
      instrument: "IE00B4L5Y983",
      kind: "ETF",
      quantity: 80,
      averageCost: 75.024875,
      totalCost: eur(6001.99),
      currentPrice: 126.08,
      marketValue: eur(10086.4),
      unrealisedGain: eur(4084.41),
      pricedAt: "2026-09-16T09:22:00Z",
      stale: false,
    },
    {
      instrument: "XX0000000000",
      kind: "EQUITY",
      quantity: 10,
      averageCost: 10,
      totalCost: eur(100),
      currentPrice: null,
      marketValue: eur(100),
      unrealisedGain: eur(0),
      pricedAt: null,
      stale: false,
    },
  ],
  stale: false,
  warnings: ["No quote for XX0000000000; valued at its cost basis"],
};

export const performance: Performance = {
  currency: "EUR",
  netInvested: eur(359950),
  currentValue: eur(434604.94),
  netGain: eur(74654.94),
  netGainPercent: 20.7404,
  annualisedReturnPercent: 3.1234,
  series: [
    { date: "2025-10-31", value: 420000 },
    { date: "2026-03-31", value: 428000 },
    { date: "2026-08-31", value: 433000 },
  ],
};
