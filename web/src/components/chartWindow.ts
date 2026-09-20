import { useSearchParams } from "react-router";
import type { DateWindow } from "../api/hooks";
import { toIsoDate } from "../format";

export const WINDOWS = [
  { id: "1a", label: "1 an", years: 1 },
  { id: "3a", label: "3 ans", years: 3 },
  { id: "5a", label: "5 ans", years: 5 },
  { id: "tout", label: "Tout", years: null },
] as const;

export type WindowId = (typeof WINDOWS)[number]["id"];

const DEFAULT_WINDOW: WindowId = "1a";
const PARAM = "periode";

function isWindowId(value: string | null): value is WindowId {
  return WINDOWS.some((window) => window.id === value);
}

export function windowFor(id: WindowId, today: Date = new Date()): DateWindow {
  const years = WINDOWS.find((window) => window.id === id)?.years ?? null;
  const from =
    years === null
      ? new Date(1900, 0, 1)
      : new Date(today.getFullYear() - years, today.getMonth(), today.getDate());
  return { from: toIsoDate(from), to: toIsoDate(today) };
}

/**
 * The chart window lives in the URL (`?periode=3a`), so a view can be bookmarked or shared and the
 * back button undoes a change of period.
 */
export function useChartWindow(): [WindowId, DateWindow, (id: WindowId) => void] {
  const [params, setParams] = useSearchParams();
  const requested = params.get(PARAM);
  const id = isWindowId(requested) ? requested : DEFAULT_WINDOW;

  const select = (next: WindowId) => {
    setParams(
      (current) => {
        const updated = new URLSearchParams(current);
        if (next === DEFAULT_WINDOW) {
          updated.delete(PARAM);
        } else {
          updated.set(PARAM, next);
        }
        return updated;
      },
      { replace: false },
    );
  };

  return [id, windowFor(id), select];
}
