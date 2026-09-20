/**
 * Every number and date on screen goes through here, in French conventions: "23 671,55 €",
 * "5,45 %", "16 sept. 2026". Formatters are cached; building an Intl formatter is not free.
 */

const LOCALE = "fr-FR";

const cache = new Map<string, Intl.NumberFormat>();

function numberFormat(key: string, options: Intl.NumberFormatOptions): Intl.NumberFormat {
  let format = cache.get(key);
  if (!format) {
    format = new Intl.NumberFormat(LOCALE, options);
    cache.set(key, format);
  }
  return format;
}

export function formatMoney(amount: number, currency: string): string {
  return numberFormat(`money:${currency}`, { style: "currency", currency }).format(amount);
}

/** Signed, for gains: "+2 000,00 €", "−350,00 €", and a plain "0,00 €". */
export function formatSignedMoney(amount: number, currency: string): string {
  return numberFormat(`signed:${currency}`, {
    style: "currency",
    currency,
    signDisplay: "exceptZero",
  }).format(amount);
}

/** Axis ticks: "250 k €" rather than "250 000,00 €". */
export function formatCompactMoney(amount: number, currency: string): string {
  return numberFormat(`compact:${currency}`, {
    style: "currency",
    currency,
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(amount);
}

/** The API sends percentages in points: 12.5 means 12.5 %. */
export function formatPercent(points: number, { signed = false } = {}): string {
  return numberFormat(`percent:${signed}`, {
    style: "percent",
    minimumFractionDigits: 1,
    maximumFractionDigits: 2,
    signDisplay: signed ? "exceptZero" : "auto",
  }).format(points / 100);
}

/** Up to eight decimals: fractions of a bitcoin are real quantities. */
export function formatQuantity(quantity: number): string {
  return numberFormat("quantity", { maximumFractionDigits: 8 }).format(quantity);
}

export function formatPrice(price: number, currency: string): string {
  return numberFormat(`price:${currency}`, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 8,
  }).format(price);
}

const dateFormat = new Intl.DateTimeFormat(LOCALE, { dateStyle: "medium" });
const monthFormat = new Intl.DateTimeFormat(LOCALE, { month: "short", year: "numeric" });
const dateTimeFormat = new Intl.DateTimeFormat(LOCALE, {
  dateStyle: "medium",
  timeStyle: "short",
});

/**
 * Reads "2026-09-16" as that calendar day where the reader is. `new Date("2026-09-16")` would read
 * it as midnight UTC, which is the previous evening in America and shows the wrong date there.
 */
export function parseIsoDate(iso: string): Date {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1);
}

export function toIsoDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export function formatDate(iso: string): string {
  return dateFormat.format(parseIsoDate(iso));
}

export function formatMonth(date: Date): string {
  return monthFormat.format(date);
}

/** An instant, such as when a price was observed, in the reader's own time zone. */
export function formatDateTime(instant: string): string {
  return dateTimeFormat.format(new Date(instant));
}
