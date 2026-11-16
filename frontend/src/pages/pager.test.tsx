import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { Pager } from "./pager";

const PAGE_SIZE = 20;

/** A pager over `rows` rows starting at `startIndex`, of `totalResults` in all. */
function renderPager(
  { rows, startIndex, totalResults }: { rows: number; startIndex: number; totalResults: number },
  disabled?: boolean,
  onePage?: "hidden" | "shown",
) {
  const onMove = vi.fn<(startIndex: number) => void>();
  const { container } = render(
    <Pager
      disabled={disabled}
      label="Widget pages"
      noun="widgets"
      onMove={onMove}
      onePage={onePage}
      page={{ rows: Array.from({ length: rows }), startIndex, totalResults }}
      pageSize={PAGE_SIZE}
    />,
  );
  return { container, onMove };
}

const previous = () => screen.getByRole("button", { name: "Previous" });
const next = () => screen.getByRole("button", { name: "Next" });
const pageNumbers = () =>
  screen
    .getAllByRole("button", { name: /^Page / })
    .map((button) => button.getAttribute("aria-label"));

describe("Pager", () => {
  it("is named by its label", () => {
    renderPager({ rows: 20, startIndex: 1, totalResults: 45 });

    expect(screen.getByRole("navigation", { name: "Widget pages" })).toBeInTheDocument();
  });

  it("names the range shown and the total", () => {
    renderPager({ rows: 20, startIndex: 21, totalResults: 45 });

    expect(screen.getByText(/^Showing 21 to 40 of 45 widgets$/)).toBeInTheDocument();
  });

  it.each([
    ["Previous", "on the first page", { rows: 20, startIndex: 1, totalResults: 45 }, false],
    ["Previous", "from the second row on", { rows: 20, startIndex: 2, totalResults: 45 }, true],
    ["Next", "while rows lie past this page", { rows: 20, startIndex: 1, totalResults: 21 }, true],
    [
      "Next",
      "once this page reaches the last row",
      { rows: 5, startIndex: 21, totalResults: 25 },
      false,
    ],
  ] as const)("%s %s is enabled: %s", (move, _, shown, enabled) => {
    renderPager(shown);

    expect(screen.getByRole("button", { name: move }).hasAttribute("disabled")).toBe(!enabled);
  });

  it("moves back by one page", async () => {
    const { onMove } = renderPager({ rows: 20, startIndex: 41, totalResults: 80 });

    await userEvent.click(previous());

    expect(onMove).toHaveBeenCalledExactlyOnceWith(21);
  });

  it("never moves back before the first row", async () => {
    const { onMove } = renderPager({ rows: 20, startIndex: 5, totalResults: 80 });

    await userEvent.click(previous());

    expect(onMove).toHaveBeenCalledExactlyOnceWith(1);
  });

  it("moves on from where a short page ended", async () => {
    const { onMove } = renderPager({ rows: 7, startIndex: 21, totalResults: 80 });

    await userEvent.click(next());

    expect(onMove).toHaveBeenCalledExactlyOnceWith(28);
  });

  it.each(["hidden", "shown"] as const)(
    "renders nothing for an empty first page, its one page %s",
    (onePage) => {
      const { container } = renderPager(
        { rows: 0, startIndex: 1, totalResults: 0 },
        false,
        onePage,
      );

      expect(container).toBeEmptyDOMElement();
    },
  );

  it("renders nothing while every row sits on the first page", () => {
    const { container } = renderPager({ rows: 20, startIndex: 1, totalResults: 20 });

    expect(container).toBeEmptyDOMElement();
  });

  it("shows the range and its one page while every row sits on the first page, when asked to", () => {
    renderPager({ rows: 3, startIndex: 1, totalResults: 3 }, false, "shown");

    expect({
      next: next().hasAttribute("disabled"),
      pages: pageNumbers(),
      previous: previous().hasAttribute("disabled"),
      range: screen.getByText(/^Showing 1 to 3 of 3 widgets$/).tagName,
    }).toEqual({ next: true, pages: ["Page 1"], previous: true, range: "P" });
  });

  it("numbers every page, marking the one shown", () => {
    renderPager({ rows: 20, startIndex: 21, totalResults: 45 });

    expect(pageNumbers()).toEqual(["Page 1", "Page 2", "Page 3"]);
    expect(screen.getByRole("button", { name: "Page 2" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByRole("button", { name: "Page 1" })).not.toHaveAttribute("aria-current");
    expect(screen.getByRole("button", { name: "Page 2" })).toHaveAttribute(
      "data-variant",
      "default",
    );
    expect(screen.getByRole("button", { name: "Page 1" })).toHaveAttribute(
      "data-variant",
      "outline",
    );
  });

  it.each([
    [1, "« 1 2 3 4 … 10 »"],
    [4, "« 1 2 3 4 5 … 10 »"],
    [5, "« 1 … 4 5 6 … 10 »"],
    [7, "« 1 … 6 7 8 9 10 »"],
    [10, "« 1 … 7 8 9 10 »"],
  ])("past seven pages, numbers the ends and the neighbours of page %i", (shown, items) => {
    renderPager({ rows: 20, startIndex: (shown - 1) * PAGE_SIZE + 1, totalResults: 200 });

    expect(
      screen
        .getAllByRole("listitem")
        .map((item) => (item.textContent === "More pages" ? "…" : item.textContent))
        .join(" "),
    ).toBe(items);
  });

  it("marks the page a page's first row falls on, wherever that row is", () => {
    renderPager({ rows: 20, startIndex: 40, totalResults: 80 });

    expect(screen.getByRole("button", { name: "Page 2" })).toHaveAttribute("aria-current", "page");
  });

  it("numbers a lone page once", () => {
    renderPager({ rows: 1, startIndex: 2, totalResults: 2 });

    expect(pageNumbers()).toEqual(["Page 1"]);
  });

  it("numbers all seven of seven pages", () => {
    renderPager({ rows: 20, startIndex: 61, totalResults: 140 });

    expect(pageNumbers()).toHaveLength(7);
  });

  it("moves to the first row of the page numbered", async () => {
    const { onMove } = renderPager({ rows: 20, startIndex: 1, totalResults: 45 });

    await userEvent.click(screen.getByRole("button", { name: "Page 3" }));

    expect(onMove).toHaveBeenCalledExactlyOnceWith(41);
  });

  it("does not move for the page shown", async () => {
    const { onMove } = renderPager({ rows: 20, startIndex: 21, totalResults: 45 });

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect(onMove).not.toHaveBeenCalled();
  });

  it("keeps an empty later page's Previous, naming the count instead of a backwards range", () => {
    renderPager({ rows: 0, startIndex: 21, totalResults: 3 });

    expect(screen.getByText(/^Showing 0 of 3 widgets$/)).toBeInTheDocument();
    expect(previous()).toBeEnabled();
    expect(next()).toBeDisabled();
  });

  it("holds both moves still while disabled", () => {
    renderPager({ rows: 20, startIndex: 21, totalResults: 80 }, true);

    expect(previous()).toBeDisabled();
    expect(next()).toBeDisabled();
    expect(screen.getByRole("button", { name: "Page 1" })).toBeDisabled();
  });
});
