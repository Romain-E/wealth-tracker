import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { formatCompactMoney, formatMoney } from "../format";
import { exactly, including } from "../test/text";
import { ValueChart } from "./ValueChart";

const points = [
  { date: "2025-10-31", value: 420000 },
  { date: "2026-03-31", value: 428000 },
  { date: "2026-08-31", value: 433000 },
];

describe("ValueChart", () => {
  it("says there is not enough history rather than drawing a single dot", () => {
    render(<ValueChart title="Valeur" points={points.slice(0, 1)} currency="EUR" />);
    expect(screen.getByText(/pas encore assez d’historique/i)).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  it("labels the last point directly, and nothing else", () => {
    const { container } = render(<ValueChart title="Valeur" points={points} currency="EUR" />);
    const labels = container.querySelectorAll(".end-label");
    expect(labels).toHaveLength(1);
    expect(labels[0]).toHaveTextContent(including(formatCompactMoney(433000, "EUR")));
  });

  it("can be read point by point from the keyboard", async () => {
    const user = userEvent.setup();
    render(<ValueChart title="Valeur" points={points} currency="EUR" />);

    await user.tab();
    expect(screen.getByRole("img")).toHaveFocus();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("status")).toHaveTextContent(including(formatMoney(428000, "EUR")));
    await user.keyboard("{Home}");
    expect(screen.getByRole("status")).toHaveTextContent(including(formatMoney(420000, "EUR")));
  });

  it("offers every value as a table, so nothing depends on hovering", async () => {
    const user = userEvent.setup();
    render(<ValueChart title="Valeur" points={points} currency="EUR" />);

    await user.click(screen.getByRole("button", { name: /afficher les valeurs/i }));

    const table = screen.getByRole("table", { name: "Valeur" });
    expect(within(table).getAllByRole("row")).toHaveLength(points.length + 1);
    expect(within(table).getByText(exactly(formatMoney(433000, "EUR")))).toBeInTheDocument();
  });
});
