/*
 * French formatting separates thousands with a narrow no-break space and puts a no-break space
 * before "€". Testing Library collapses those to plain spaces on the page side only, so an expected
 * string built with the same formatter would never match. These helpers turn it into a pattern that
 * accepts any kind of space, keeping the tests written in terms of the real formatter.
 */

const SPACES = /[\s\u00a0\u202f]+/g;

function pattern(expected: string): string {
  return expected.replace(/[.*+?^${}()|[\]\\]/g, "\\$&").replace(SPACES, "\\s+");
}

/** The whole text of an element, for `getByText` and friends. */
export function exactly(expected: string): RegExp {
  return new RegExp(`^${pattern(expected)}$`);
}

/** Part of an element's text, for `toHaveTextContent`. */
export function including(expected: string): RegExp {
  return new RegExp(pattern(expected));
}
