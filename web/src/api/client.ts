import createClient from "openapi-fetch";
import type { components, paths } from "./generated";

type Schemas = components["schemas"];

export type Portfolio = Schemas["PortfolioResponse"];
export type AccountSummary = Schemas["AccountSummary"];
export type TypeSlice = Schemas["TypeSlice"];
export type CategorySlice = Schemas["CategorySlice"];
export type AccountDetail = Schemas["AccountDetailResponse"];
export type Position = Schemas["PositionResponse"];
export type Performance = Schemas["PerformanceResponse"];
export type Money = Schemas["MoneyResponse"];
export type TransactionRequest = Schemas["TransactionRequest"];

/**
 * The typed client, generated from the API's own contract (openapi.json). A path, a parameter or
 * a field that does not exist on the server does not compile here.
 *
 * Requests go to the page's own origin: the dev server and nginx both forward /api to the backend.
 * `fetch` is looked up on every call rather than captured once, so test doubles installed after
 * this module loads are still used.
 */
export const api = createClient<paths>({
  baseUrl: globalThis.location.origin,
  fetch: (request) => globalThis.fetch(request),
});

/** An RFC 7807 problem document, as the API returns for every error. */
export type Problem = Schemas["Problem"];

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly problem: Problem | undefined,
  ) {
    super(problem?.detail ?? `HTTP ${status}`);
    this.name = "ApiError";
  }

  /** Worth retrying: the server or the network failed, the request itself was fine. */
  get isTransient(): boolean {
    return this.status >= 500 || this.status === 0;
  }
}

interface FetchResult<T> {
  data?: T;
  error?: unknown;
  response: Response;
}

/** Returns the body of a successful call, or throws an {@link ApiError} carrying the problem. */
export async function unwrap<T>(call: Promise<FetchResult<T>>): Promise<T> {
  const { data, error, response } = await call;
  if (!response.ok || data === undefined) {
    throw new ApiError(response.status, isProblem(error) ? error : undefined);
  }
  return data;
}

function isProblem(value: unknown): value is Problem {
  return typeof value === "object" && value !== null;
}
