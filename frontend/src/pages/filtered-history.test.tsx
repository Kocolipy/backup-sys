import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import type { ReadDeclaration } from "@/auth/declarations";
import { InPageStatus, shownLine } from "@/auth/page-status.testHelpers";
import {
  bare,
  deferred,
  holding,
  json,
  renderAsSession,
  type Answers,
} from "@/auth/session-adapter.testHelpers";
import { jsonDecoder, readObject } from "@/lib/decode";
import { pageDecoder, pagedPath, type Page, type PageAt } from "@/lib/paging";
import { sortParameters, type ColumnSort } from "@/lib/sorting";

import { FilteredHistory } from "./filtered-history";

// The filtered history over a history of its own: a patient's widgets, two a
// page, narrowed by one filter and sorted by one column. What each history
// page hands it — its read, its Filter panel, its copy, its table's columns —
// is that page's suite's (ffi.test.tsx, numc.test.tsx, armc.test.tsx,
// medication.test.tsx); the paged read beneath it is paged-read.test.tsx's.

interface WidgetFilters {
  kind: string;
}

const NO_FILTERS: WidgetFilters = { kind: "" };

const decodeWidget = (value: unknown): string => readObject(value, "Widget").string("name");

/** A patient's widgets, as a history's API module declares its read: a `404` is no patient. */
const WIDGET_HISTORY: ReadDeclaration<
  Page<string>,
  [patientId: string, filters: WidgetFilters, sort: ColumnSort<"made"> | null, at: PageAt],
  true
> = {
  absentOnNotFound: true,
  decode: jsonDecoder(pageDecoder("WidgetPage", "widgets", decodeWidget)),
  path: (patientId, filters, sort, at) =>
    pagedPath(
      `/api/patient-widgets/${patientId}`,
      at,
      [...(filters.kind === "" ? [] : [`kind=${filters.kind}`]), ...sortParameters(sort)].join("&"),
    ),
  permission: "patient:read",
};

/** The read of the patient's widgets from `startIndex`, with `asked` — a filter, a sort — before the page. */
const widgetsAt = (startIndex: number, asked = "") =>
  `GET /api/patient-widgets/p-1?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=2`;

const widgetPage = (names: string[], startIndex: number, totalResults: number) =>
  json({ startIndex, totalResults, widgets: names.map((name) => ({ name })) });

const FIRST_OF_THREE = widgetPage(["anvil", "bolt"], 1, 3);

/** The history of the patient `p-1`, its filter one button and its sort another. */
const renderHistory = (answers: Answers) =>
  renderAsSession(
    <InPageStatus>
      <FilteredHistory
        copy={{
          empty: "No widgets",
          emptyFiltered: "No widgets match the filters",
          failure: "Couldn't load widgets",
          loading: "Loading widgets…",
          noun: "widgets",
          pagerLabel: "Widget pages",
        }}
        countFilters={(filters: WidgetFilters) => (filters.kind === "" ? 0 : 1)}
        declaration={WIDGET_HISTORY}
        filterPopover={({ applied, onApply }) => (
          <button onClick={() => onApply({ kind: "bolt" })} type="button">
            Bolts only, of {applied.kind === "" ? "all" : applied.kind}
          </button>
        )}
        noFilters={NO_FILTERS}
        pageSize={2}
        patientId="p-1"
        table={({ pending, rows, sorting }) => (
          <>
            <button
              onClick={() => sorting.onSort({ by: "made", order: "ascending" })}
              type="button"
            >
              Sort by made, now {sorting.sort?.by ?? "unsorted"}
            </button>
            <p data-pending={pending} data-testid="rows">
              {rows.join(", ")}
            </p>
          </>
        )}
      />
    </InPageStatus>,
    holding(["patient:read"]),
    { answers },
  );

const reads = (sent: readonly { method: string; path: string }[]) =>
  sent.map(({ method, path }) => `${method} ${path}`);

const press = (name: RegExp) => userEvent.click(screen.getByRole("button", { name }));

describe("FilteredHistory", () => {
  it("reads the patient's history from its first page, unfiltered and unsorted, naming the range on a single page too", async () => {
    const { requests } = await renderHistory({
      [widgetsAt(1)]: widgetPage(["anvil", "bolt"], 1, 2),
    });

    expect(await screen.findByText("Showing 1 to 2 of 2 widgets")).toBeInTheDocument();
    expect(screen.getByTestId("rows")).toHaveTextContent(/^anvil, bolt$/);
    expect(reads(requests())).toEqual([widgetsAt(1)]);
  });

  it("says No patient in context for a patient the source holds none under, and reports nothing", async () => {
    await renderHistory({ [widgetsAt(1)]: bare(404) });

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(shownLine()).toBeNull();
    expect(screen.queryByRole("button", { name: /^Bolts only/ })).toBeNull();
  });

  it("reports any other failure as the history's own, offering Try again", async () => {
    await renderHistory({ [widgetsAt(1)]: bare(502) });

    expect(await screen.findByRole("button", { name: "Try again" })).toBeEnabled();
    expect(shownLine()).toBe("Couldn't load widgets");
    expect(screen.queryByText("No patient in context")).toBeNull();
  });

  it("reads from the first page for a new filter, and for a new sort combined with it", async () => {
    const { requests } = await renderHistory({
      [widgetsAt(1)]: FIRST_OF_THREE,
      [widgetsAt(3)]: widgetPage(["cog"], 3, 3),
      [widgetsAt(1, "kind=bolt")]: widgetPage(["bolt", "bolt"], 1, 3),
      [widgetsAt(3, "kind=bolt")]: widgetPage(["bolt"], 3, 3),
      [widgetsAt(1, "kind=bolt&sortBy=made&sortOrder=ascending")]: widgetPage(["bolt"], 1, 1),
    });
    await screen.findByText("Showing 1 to 2 of 3 widgets");
    await press(/^Page 2$/);
    await screen.findByText("Showing 3 to 3 of 3 widgets");

    await press(/^Bolts only, of all$/);
    await screen.findByText("Showing 1 to 2 of 3 widgets");
    await press(/^Page 2$/);
    await screen.findByText("Showing 3 to 3 of 3 widgets");
    await press(/^Sort by made, now unsorted$/);
    await screen.findByText("Showing 1 to 1 of 1 widgets");

    expect({
      filter: screen.getByRole("button", { name: /^Bolts only/ }).textContent,
      reads: reads(requests()),
      sort: screen.getByRole("button", { name: /^Sort by made/ }).textContent,
    }).toEqual({
      filter: "Bolts only, of bolt",
      reads: [
        widgetsAt(1),
        widgetsAt(3),
        widgetsAt(1, "kind=bolt"),
        widgetsAt(3, "kind=bolt"),
        widgetsAt(1, "kind=bolt&sortBy=made&sortOrder=ascending"),
      ],
      sort: "Sort by made, now made",
    });
  });

  it("says the patient has none with no filter applied", async () => {
    await renderHistory({ [widgetsAt(1)]: widgetPage([], 1, 0) });

    expect((await screen.findByText(/^No widgets/)).textContent).toBe("No widgets");
  });

  it("says none matches with a filter applied, and offers the filter still", async () => {
    await renderHistory({
      [widgetsAt(1)]: FIRST_OF_THREE,
      [widgetsAt(1, "kind=bolt")]: widgetPage([], 1, 0),
    });
    await screen.findByText("Showing 1 to 2 of 3 widgets");

    await press(/^Bolts only, of all$/);

    expect((await screen.findByText(/^No widgets/)).textContent).toBe(
      "No widgets match the filters",
    );
    expect(screen.getByRole("button", { name: "Bolts only, of bolt" })).toBeInTheDocument();
  });

  it("tells its table a read is pending beside the rows shown, and that it is not once it lands", async () => {
    const next = deferred();
    await renderHistory({ [widgetsAt(1)]: FIRST_OF_THREE, [widgetsAt(3)]: next.answer });
    await screen.findByText("Showing 1 to 2 of 3 widgets");
    const before = screen.getByTestId("rows").getAttribute("data-pending");

    await press(/^Page 2$/);
    const during = screen.getByTestId("rows").getAttribute("data-pending");
    await next.settle(widgetPage(["cog"], 3, 3));
    await screen.findByText("Showing 3 to 3 of 3 widgets");

    expect({
      after: screen.getByTestId("rows").getAttribute("data-pending"),
      before,
      during,
    }).toEqual({ after: "false", before: "false", during: "true" });
  });
});
