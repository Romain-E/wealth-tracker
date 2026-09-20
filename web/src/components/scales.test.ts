import { describe, expect, it } from "vitest";
import { niceTicks, timeTicks } from "./scales";

describe("niceTicks", () => {
  it("steps on round values and ends at or above the maximum", () => {
    expect(niceTicks(434604.94)).toEqual([0, 100000, 200000, 300000, 400000, 500000]);
    expect(niceTicks(23671.55)).toEqual([0, 5000, 10000, 15000, 20000, 25000]);
  });

  it("still draws an axis for an empty or zero series", () => {
    expect(niceTicks(0)).toEqual([0, 1]);
  });
});

describe("timeTicks", () => {
  const at = (year: number, month: number) => new Date(year, month, 1).getTime();

  it("uses month starts, spaced to stay readable", () => {
    const ticks = timeTicks(at(2025, 8) + 86_400_000, at(2026, 8) + 86_400_000);
    expect(ticks.length).toBeGreaterThan(2);
    expect(ticks.length).toBeLessThanOrEqual(6);
    for (const tick of ticks) {
      expect(new Date(tick).getDate()).toBe(1);
    }
  });

  it("switches to yearly marks over long histories", () => {
    const ticks = timeTicks(at(2010, 3), at(2026, 3));
    expect(ticks.length).toBeLessThanOrEqual(6);
    expect(ticks.every((tick) => new Date(tick).getMonth() === 0)).toBe(true);
  });
});
