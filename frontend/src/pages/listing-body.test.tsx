import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { ReadView } from "@/auth/use-gated-read";

import { ListingBody, type StaleRows } from "./listing-body";
import type { Narrowing } from "./listing-copy";

function renderBody(
  view: ReadView<string[]>,
  staleRows: StaleRows = "keep",
  {
    narrowing = "none",
    pageShown,
  }: { narrowing?: Narrowing; pageShown?: { startIndex: number } | null } = {},
) {
  return render(
    <div data-testid="body">
      <ListingBody
        narrowing={narrowing}
        pageShown={pageShown}
        rows={(widgets) => widgets}
        staleRows={staleRows}
        view={view}
      >
        {(widgets) => (
          <ul>
            {widgets.map((widget) => (
              <li key={widget}>{widget}</li>
            ))}
          </ul>
        )}
      </ListingBody>
    </div>,
  );
}

const body = () => screen.getByTestId("body");

describe("ListingBody", () => {
  it("renders nothing for a session without the read's Permission", () => {
    renderBody({ kind: "not-permitted" });

    expect(body()).toBeEmptyDOMElement();
  });

  it("shows skeleton rows while the first read is in flight", () => {
    renderBody({ kind: "loading" });

    expect(body().querySelectorAll('[aria-hidden="true"] [data-slot="skeleton"]')).toHaveLength(3);
  });

  it("tells a screen reader the listing is loading while the first read is in flight", () => {
    renderBody({ kind: "loading" });

    expect(screen.getByRole("status")).toHaveTextContent(/^Loading records…$/);
  });

  it("says the loading copy of how the listing is narrowed", () => {
    renderBody({ kind: "loading" }, "keep", { narrowing: "patient-search" });

    expect(screen.getByRole("status")).toHaveTextContent(/^Searching records…$/);
  });

  it("renders the rows once the read succeeds", () => {
    renderBody({ data: ["anvil", "bolt"], kind: "ready" });

    expect(screen.getAllByRole("listitem").map((item) => item.textContent)).toEqual([
      "anvil",
      "bolt",
    ]);
  });

  // A paged read's rows are one part of what it answered; the rest — its total — is the page's too.
  it.each(["ready", "stale"] as const)(
    "hands its children the %s read their rows came from",
    (kind) => {
      render(
        <ListingBody
          narrowing="none"
          rows={(page: { total: number; widgets: string[] }) => page.widgets}
          staleRows="keep"
          view={{ data: { total: 7, widgets: ["anvil"] }, kind }}
        >
          {(widgets, page) => (
            <p>
              {widgets.length} of {page.total}
            </p>
          )}
        </ListingBody>,
      );

      expect(screen.getByText("1 of 7")).toBeInTheDocument();
    },
  );

  it("says the listing is empty when the read succeeds with no rows", () => {
    renderBody({ data: [], kind: "ready" });

    expect(body()).toHaveTextContent(/^No records found\.$/);
  });

  it("says the empty copy of how the listing is narrowed", () => {
    renderBody({ data: [], kind: "ready" }, "keep", { narrowing: "filters" });

    expect(body()).toHaveTextContent(/^No records match the filters\.$/);
  });

  it("says the listing's own empty copy on an empty first page", () => {
    renderBody({ data: [], kind: "ready" }, "keep", {
      narrowing: "search",
      pageShown: { startIndex: 1 },
    });

    expect(body()).toHaveTextContent(/^No records match your search\.$/);
  });

  it("says only that a later page emptied", () => {
    renderBody({ data: [], kind: "ready" }, "keep", {
      narrowing: "search",
      pageShown: { startIndex: 21 },
    });

    expect(body()).toHaveTextContent(/^This page is now empty\. Use Previous to go back\.$/);
  });

  it("shows a decorative icon beside the empty line", () => {
    renderBody({ data: [], kind: "ready" });

    expect(body().querySelectorAll('svg[aria-hidden="true"]')).toHaveLength(1);
  });

  // The page's error line says what went wrong; the body never claims "loading" or "empty".
  it("renders nothing for a failed first read", () => {
    renderBody({ kind: "failed" });

    expect(body()).toBeEmptyDOMElement();
  });

  it("keeps the last rows after a failed refresh when the page keeps stale rows", () => {
    renderBody({ data: ["anvil"], kind: "stale" }, "keep");

    expect(screen.getByRole("listitem")).toHaveTextContent("anvil");
  });

  it("hides the last rows after a failed refresh when the page hides stale rows", () => {
    renderBody({ data: ["anvil"], kind: "stale" }, "hide");

    expect(body()).toBeEmptyDOMElement();
  });

  it("never reports a failed refresh of an empty listing as empty", () => {
    renderBody({ data: [], kind: "stale" }, "keep");

    expect(body()).toBeEmptyDOMElement();
  });
});
