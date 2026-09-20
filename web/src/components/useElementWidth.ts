import { useCallback, useLayoutEffect, useState } from "react";

/**
 * The rendered width of an element, kept current as the layout changes. Charts draw in real pixels
 * so that a 2px line is 2px wide on every screen, instead of being scaled with the viewBox.
 *
 * The width is read once as soon as the element is attached, before the browser paints, and then
 * on every resize. Waiting for the observer's first report alone leaves the chart drawn at the
 * fallback width whenever that report comes late.
 */
export function useElementWidth(fallback: number): [(element: HTMLElement | null) => void, number] {
  const [element, setElement] = useState<HTMLElement | null>(null);
  const [width, setWidth] = useState(fallback);

  useLayoutEffect(() => {
    if (element === null) {
      return;
    }
    const measure = (measured: number) => {
      if (measured > 0) {
        setWidth(Math.round(measured));
      }
    };
    measure(element.getBoundingClientRect().width);
    // Absent in some test environments; the first measurement is all there is there.
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(([entry]) => {
      measure(entry?.contentRect.width ?? 0);
    });
    observer.observe(element);
    return () => {
      observer.disconnect();
    };
  }, [element]);

  const ref = useCallback((node: HTMLElement | null) => {
    setElement(node);
  }, []);

  return [ref, width];
}
