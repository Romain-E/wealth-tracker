import { screen, within } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";
import { formatMoney, formatPercent, toIsoDate } from "../format";
import { LIVRET_A_ID, performance, portfolio } from "../test/fixtures";
import { renderRoute } from "../test/renderRoute";
import { problem, server } from "../test/server";
import { exactly, including } from "../test/text";

describe("Dashboard", () => {
  it("leads with the total and shows where it sits", async () => {
    renderRoute("/");

    // The same figure also appears as the current value in the performance tiles.
    const heading = await screen.findByRole("heading", { level: 1, name: "Patrimoine total" });
    expect(heading.closest("header")).toHaveTextContent(including(formatMoney(434604.94, "EUR")));
    expect(screen.getByRole("listitem", { name: /^Livret A : .* du total$/ })).toBeInTheDocument();
    const accounts = screen.getByRole("table", { name: "Comptes" });
    expect(within(accounts).getByRole("link", { name: "Livret A" })).toHaveAttribute(
      "href",
      `/comptes/${LIVRET_A_ID}`,
    );
    expect(document.title).toBe("Tableau de bord · Patrimoine");
  });

  it("keeps passbooks apart from investments", async () => {
    renderRoute("/");

    const accounts = await screen.findByRole("table", { name: "Comptes" });
    // Each family is a row group, opened by a header row with its name and subtotal.
    const group = (name: string) => {
      const header = within(accounts).getByRole("rowheader", { name });
      const rows = header.closest("tbody");
      if (rows === null) throw new Error(`${name} is not a row group`);
      return { header: header.closest("tr"), rows: within(rows) };
    };

    const savings = group("Livrets d'épargne");
    expect(savings.header).toHaveTextContent(including(formatMoney(23671.55, "EUR")));
    expect(savings.rows.getByRole("link", { name: "Livret A" })).toBeInTheDocument();
    expect(savings.rows.queryByRole("link", { name: "Compte-titres" })).toBeNull();

    const investments = group("Placements");
    expect(investments.header).toHaveTextContent(including(formatMoney(12072.41, "EUR")));
    expect(investments.rows.getByRole("link", { name: "Compte-titres" })).toBeInTheDocument();
    expect(investments.rows.queryByRole("link", { name: "Livret A" })).toBeNull();

    // The allocation chart groups the same way.
    const savingsBars = screen.getByRole("list", { name: /^Livrets d'épargne : / });
    expect(within(savingsBars).getByRole("listitem", { name: /^Livret A : / })).toBeInTheDocument();
    expect(screen.getByRole("list", { name: /^Placements : / })).toContainElement(
      screen.getByRole("listitem", { name: /^Compte-titres ordinaire : / }),
    );
  });

  it("shows the long-run performance figures", async () => {
    renderRoute("/");

    expect(await screen.findByText("Rendement annualisé")).toBeInTheDocument();
    expect(screen.getByText(exactly(formatPercent(3.1234, { signed: true })))).toBeInTheDocument();
    expect(
      screen.getByText(exactly(formatMoney(performance.netInvested.amount, "EUR"))),
    ).toBeInTheDocument();
  });

  it("says when prices are not live instead of hiding it", async () => {
    server.use(
      http.get("*/api/v1/portfolio", () => HttpResponse.json({ ...portfolio, stale: true })),
    );
    renderRoute("/");

    expect(await screen.findByText("Certains prix ne sont pas à jour")).toBeInTheDocument();
  });

  it("asks for a longer history when the period changes", async () => {
    const requested: URLSearchParams[] = [];
    server.use(
      http.get("*/api/v1/performance", ({ request }) => {
        requested.push(new URL(request.url).searchParams);
        return HttpResponse.json(performance);
      }),
    );
    const { user, router } = renderRoute("/");

    await user.click(await screen.findByRole("radio", { name: "3 ans" }));

    const today = new Date();
    const threeYearsAgo = new Date(today.getFullYear() - 3, today.getMonth(), today.getDate());
    await screen.findByRole("radio", { name: "3 ans", checked: true });
    expect(requested.at(-1)?.get("from")).toBe(toIsoDate(threeYearsAgo));
    expect(requested.at(-1)?.get("to")).toBe(toIsoDate(today));
    expect(router.state.location.search).toBe("?periode=3a");
  });

  it("explains a server failure and gives the reference to quote", async () => {
    server.use(
      http.get("*/api/v1/portfolio", () =>
        problem(500, { code: "internal-error", detail: "The request could not be processed." }),
      ),
    );
    renderRoute("/");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Impossible de joindre le serveur");
    expect(alert).toHaveTextContent("test-correlation-id");
  });
});
