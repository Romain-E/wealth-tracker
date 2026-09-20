import { describe, expect, it } from "vitest";
import {
  formatMoney,
  formatPercent,
  formatQuantity,
  formatSignedMoney,
  parseIsoDate,
  toIsoDate,
} from "./format";

// French formatting uses narrow and regular non-breaking spaces; compare on plain spaces.
const plain = (text: string) => text.replace(/[\u202f\u00a0]/g, " ");

describe("format", () => {
  it("writes amounts the French way", () => {
    expect(plain(formatMoney(23671.55, "EUR"))).toBe("23 671,55 €");
  });

  it("signs gains and losses, but not zero", () => {
    expect(plain(formatSignedMoney(2000, "EUR"))).toBe("+2 000,00 €");
    expect(plain(formatSignedMoney(-350, "EUR"))).toMatch(/^[-−]350,00 €$/);
    expect(plain(formatSignedMoney(0, "EUR"))).toBe("0,00 €");
  });

  it("reads percentages as points, as the API sends them", () => {
    expect(plain(formatPercent(5.4467))).toBe("5,45 %");
    expect(plain(formatPercent(3.1, { signed: true }))).toBe("+3,1 %");
  });

  it("keeps the eight decimals a crypto quantity can have", () => {
    expect(formatQuantity(0.05000001)).toBe("0,05000001");
  });

  it("reads an ISO date as that calendar day, whatever the time zone", () => {
    const date = parseIsoDate("2026-01-01");
    expect([date.getFullYear(), date.getMonth(), date.getDate()]).toEqual([2026, 0, 1]);
    expect(toIsoDate(date)).toBe("2026-01-01");
  });
});
