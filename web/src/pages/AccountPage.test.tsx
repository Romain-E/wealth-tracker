import { screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import type { TransactionRequest } from "../api/client";
import { formatMoney, toIsoDate } from "../format";
import { CTO_ID, LIVRET_A_ID, livretA } from "../test/fixtures";
import { renderRoute } from "../test/renderRoute";
import { problem, server } from "../test/server";
import { including } from "../test/text";

describe("Account page", () => {
  it("shows how much of the Livret A ceiling is used", async () => {
    renderRoute(`/comptes/${LIVRET_A_ID}`);

    const meter = await screen.findByRole("meter", { name: "Plafond des versements" });
    expect(meter).toHaveAttribute("aria-valuenow", "22450");
    expect(meter).toHaveAttribute("aria-valuemax", "22950");
    expect(
      screen.getByText(including(`encore ${formatMoney(500, "EUR")} autorisés`)),
    ).toBeInTheDocument();
  });

  it("says an uncapped envelope has no ceiling, rather than showing an empty meter", async () => {
    renderRoute(`/comptes/${CTO_ID}`);

    expect(
      await screen.findByText("Aucun plafond de versement pour cette enveloppe."),
    ).toBeInTheDocument();
    expect(screen.queryByRole("meter")).not.toBeInTheDocument();
  });

  it("marks an unpriced position instead of inventing a price", async () => {
    renderRoute(`/comptes/${CTO_ID}`);

    expect(await screen.findByText("non coté")).toBeInTheDocument();
    expect(screen.getByText(/valued at its cost basis/)).toBeInTheDocument();
  });

  it("records a deposit and refreshes the figures", async () => {
    const sent: TransactionRequest[] = [];
    let accountLoads = 0;
    server.use(
      http.get(`*/api/v1/accounts/${LIVRET_A_ID}`, () => {
        accountLoads += 1;
        return HttpResponse.json(livretA);
      }),
      http.post(`*/api/v1/accounts/${LIVRET_A_ID}/transactions`, async ({ request }) => {
        sent.push((await request.json()) as TransactionRequest);
        return HttpResponse.json({}, { status: 201 });
      }),
    );
    const { user } = renderRoute(`/comptes/${LIVRET_A_ID}`);

    await user.type(await screen.findByLabelText(/Montant/), "100,50");
    await user.click(screen.getByRole("button", { name: "Enregistrer" }));

    expect(await screen.findByText("Mouvement enregistré.")).toBeInTheDocument();
    expect(sent).toEqual([
      {
        type: "DEPOSIT",
        date: toIsoDate(new Date()),
        amount: { amount: 100.5, currency: "EUR" },
      },
    ]);
    await waitFor(() => {
      expect(accountLoads).toBeGreaterThanOrEqual(2);
    });
  });

  it("shows the rule that refused a movement, in the server's own words", async () => {
    server.use(
      http.post(`*/api/v1/accounts/${LIVRET_A_ID}/transactions`, () =>
        problem(422, {
          code: "deposit-ceiling-exceeded",
          detail: "Livret A payments are capped at 22950.00 EUR",
        }),
      ),
    );
    const { user } = renderRoute(`/comptes/${LIVRET_A_ID}`);

    await user.type(await screen.findByLabelText(/Montant/), "5000");
    await user.click(screen.getByRole("button", { name: "Enregistrer" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Opération refusée");
    expect(alert).toHaveTextContent("Livret A payments are capped at 22950.00 EUR");
  });

  it("says so when the account does not exist", async () => {
    renderRoute("/comptes/00000000-0000-4000-a000-00000000dead");

    expect(await screen.findByRole("alert")).toHaveTextContent("Introuvable");
  });
});
