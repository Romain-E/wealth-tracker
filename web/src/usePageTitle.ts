import { useEffect } from "react";

/** Each page names itself in the tab and in the history, which screen readers announce. */
export function usePageTitle(title: string | undefined) {
  useEffect(() => {
    document.title = title ? `${title} · Patrimoine` : "Patrimoine";
  }, [title]);
}
