import { HttpResponse, http } from "msw";
import { setupServer } from "msw/node";
import type { Problem } from "../api/client";
import { CTO_ID, LIVRET_A_ID, compteTitres, livretA, performance, portfolio } from "./fixtures";

/*
 * The API, mocked at the network level: the application code under test is the real code, fetch
 * included. These defaults describe a healthy server; a test overrides one with server.use().
 */
export const handlers = [
  http.get("*/api/v1/portfolio", () => HttpResponse.json(portfolio)),
  http.get("*/api/v1/performance", () => HttpResponse.json(performance)),
  http.get("*/api/v1/accounts/:accountId/performance", () => HttpResponse.json(performance)),
  http.get("*/api/v1/accounts/:accountId", ({ params }) => {
    if (params.accountId === LIVRET_A_ID) {
      return HttpResponse.json(livretA);
    }
    if (params.accountId === CTO_ID) {
      return HttpResponse.json(compteTitres);
    }
    return problem(404, {
      code: "resource-not-found",
      detail: `Account ${String(params.accountId)} was not found`,
    });
  }),
];

export const server = setupServer(...handlers);

/** A problem document shaped exactly like the API's. */
export function problem(status: number, fields: Partial<Problem>) {
  return HttpResponse.json(
    {
      type: `/problems/${fields.code ?? "internal-error"}`,
      title: "Error",
      status,
      correlationId: "test-correlation-id",
      ...fields,
    },
    { status, headers: { "Content-Type": "application/problem+json" } },
  );
}
