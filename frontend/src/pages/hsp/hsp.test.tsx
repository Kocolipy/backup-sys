import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { leadingCells, leadingColumn, warnings } from "@/pages/mandatory-elements.testHelpers";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which the HSP page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * The reads of Tan Wei Ming's HSP History, as answer-table keys. Written out
 * rather than built from `HSP_HISTORY.path`, so the path is checked against
 * the contract and not against the code under test.
 */
const FIRST_PAGE = "GET /api/patient-hsp/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5";
const SECOND_PAGE = "GET /api/patient-hsp/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5";

/**
 * One HSP Record as the backend sends it, its fields given by `fields` over a
 * LOW, Active record's, none invalid unless `fields` names some.
 */
const record = (
  id: string | null,
  fields: Record<string, string | null | readonly string[]> = {},
) => ({
  endDate: "2025-03-10",
  id,
  invalid: [],
  outcome: "low",
  startDate: "2024-03-11",
  status: "active",
  submittingMc: "nee-soon-camp",
  ...fields,
});

/** Tan Wei Ming's three HSP Records, as the patient source's mock serves them. */
const THREE_RECORDS = [
  record("hsp-tan-wei-ming-1"),
  record("hsp-tan-wei-ming-2", {
    endDate: "2024-10-13",
    outcome: "moderate",
    startDate: "2023-10-14",
    status: "inactive",
    submittingMc: "mandai-hill-camp",
  }),
  record("hsp-tan-wei-ming-3", {
    endDate: "2023-10-10",
    outcome: "high",
    startDate: "2022-11-22",
    status: "inactive",
    submittingMc: "hendon-camp",
  }),
];

const page = (records: readonly object[], startIndex: number, totalResults: number) =>
  json({ records, startIndex, totalResults });

/** The HSP page with Tan Wei Ming in context, his HSP History's first page answered `answer`. */
async function renderHsp(answer: Answers[string], more: Answers = {}) {
  withPatientInContext(TAN_WEI_MING.id);
  return renderSignedIn("/hsp", CLINICIAN, {
    [FIRST_PAGE]: answer,
    [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    ...more,
  });
}

/** The HSP reads among what the page sent. */
const hspReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient-hsp/"));

const table = () => screen.getByRole("table", { name: "HSP History" });

/** The body rows' cells, each row's leading warning cell, where shown, left out. */
const dataCells = () => {
  const leading = leadingCells(table());
  return within(table())
    .getAllByRole("row")
    .slice(1)
    .map((row) => within(row).getAllByRole("cell").slice(leading));
};

/** Each body row's data cells, as text. */
const rows = () => dataCells().map((cells) => cells.map((cell) => cell.textContent));

/** The Outcome badge of each body row, by its text and its variant. */
const outcomeBadges = () =>
  dataCells().map(([outcome]) => {
    const badge = outcome!.querySelector("[data-slot=badge]");
    const white = badge?.classList.contains("bg-card") ? " white" : "";
    return `${badge?.textContent} ${badge?.getAttribute("data-variant")}${white}`;
  });

/** The Status of each body row, by its text and its colour: the class its text is drawn in. */
const statusTones = () =>
  dataCells().map((cells) => {
    const status = cells[4]!;
    return `${status.textContent} [${status.firstElementChild?.className ?? "no span"}]`;
  });

afterEach(() => {
  clearEhrContext();
});

describe("HSP", () => {
  it("shows the patient in context's HSP Records in the source's order, each code by its label", async () => {
    await renderHsp(page(THREE_RECORDS, 1, 3));

    expect({
      headings: within(await screen.findByRole("table", { name: "HSP History" }))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
    }).toEqual({
      headings: ["Outcome", "Start Date", "End Date", "Submitting MC", "Status"],
      rows: [
        ["LOW", "11 Mar 2024", "10 Mar 2025", "Nee Soon Camp MC", "Active"],
        ["MODERATE", "14 Oct 2023", "13 Oct 2024", "Mandai Hill Camp MC", "Inactive"],
        ["HIGH", "22 Nov 2022", "10 Oct 2023", "Hendon Camp MC", "Inactive"],
      ],
    });
  });

  it("shows a Submitting MC of Khatib Camp MC by its label", async () => {
    await renderHsp(page([record("hsp-khatib", { submittingMc: "khatib-camp" })], 1, 1));
    await screen.findByRole("table", { name: "HSP History" });

    expect(rows()[0]![3]).toBe("Khatib Camp MC");
  });

  it("shows each Outcome as a badge: LOW green, MODERATE amber, HIGH red", async () => {
    await renderHsp(page(THREE_RECORDS, 1, 3));
    await screen.findByRole("table", { name: "HSP History" });

    expect(outcomeBadges()).toEqual(["LOW success", "MODERATE warning", "HIGH danger"]);
  });

  it("shows each Status as coloured text, not a badge: Active green, Inactive muted", async () => {
    await renderHsp(page(THREE_RECORDS, 1, 3));
    await screen.findByRole("table", { name: "HSP History" });

    expect({
      badges: table().querySelectorAll("td:last-child [data-slot=badge]").length,
      tones: statusTones(),
    }).toEqual({
      badges: 0,
      tones: [
        "Active [text-success-soft-foreground]",
        "Inactive [text-muted-foreground]",
        "Inactive [text-muted-foreground]",
      ],
    });
  });

  // Status is the source's: a record past its End Date shows the Status the
  // source sent, never one worked out from the date.
  it("shows the source's Status even for a record past its End Date", async () => {
    await renderHsp(page([record("lapsed", { endDate: "2001-02-05", status: "active" })], 1, 1));
    await screen.findByRole("table", { name: "HSP History" });

    expect(rows()).toEqual([["LOW", "11 Mar 2024", "05 Feb 2001", "Nee Soon Camp MC", "Active"]]);
  });

  // The patient source is the truth (ADR 0020): its data is shown as received.
  it('shows a missing field as "—", the rest of the row as any other', async () => {
    await renderHsp(
      page(
        [
          record("hsp-1", { outcome: null, submittingMc: null }),
          record("hsp-2", { endDate: null, startDate: null, status: null }),
        ],
        1,
        2,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect(rows()).toEqual([
      ["—", "11 Mar 2024", "10 Mar 2025", "—", "Active"],
      ["LOW", "—", "—", "Nee Soon Camp MC", "—"],
    ]);
  });

  it('shows a field in the wrong format as "Error", the rest of the row as any other', async () => {
    await renderHsp(
      page(
        [
          record("hsp-1", {
            endDate: null,
            invalid: ["startDate", "endDate", "status"],
            startDate: null,
            status: null,
          }),
          record("hsp-2", {
            invalid: ["outcome", "submittingMc"],
            outcome: null,
            submittingMc: null,
          }),
        ],
        1,
        2,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect(rows()).toEqual([
      ["LOW", "Error", "Error", "Nee Soon Camp MC", "Error"],
      ["Error", "11 Mar 2024", "10 Mar 2025", "Error", "Active"],
    ]);
  });

  it("shows a code outside each closed set as it came, the Outcome on a neutral badge and the Status uncoloured", async () => {
    await renderHsp(
      page(
        [
          record(null, {
            outcome: "critical",
            status: "expired",
            submittingMc: "tengah-air-base",
          }),
        ],
        1,
        1,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect({ outcomes: outcomeBadges(), rows: rows(), statuses: statusTones() }).toEqual({
      outcomes: ["critical outline white"],
      rows: [["critical", "11 Mar 2024", "10 Mar 2025", "tengah-air-base", "expired"]],
      statuses: ["expired []"],
    });
  });

  // A code spelling an Object member is no member of a closed set.
  it("shows a code that names an Object member as it came", async () => {
    await renderHsp(
      page(
        [record("hsp-1", { outcome: "toString", status: "valueOf", submittingMc: "__proto__" })],
        1,
        1,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect({ outcomes: outcomeBadges(), rows: rows(), statuses: statusTones() }).toEqual({
      outcomes: ["toString outline white"],
      rows: [["toString", "11 Mar 2024", "10 Mar 2025", "__proto__", "valueOf"]],
      statuses: ["valueOf []"],
    });
  });

  it("keys a row with no id by its place, as React needs each row keyed", async () => {
    const warned = vi.spyOn(console, "error").mockImplementation(() => undefined);
    await renderHsp(
      page(
        [record(null), record(null, { invalid: ["id"] }), record("hsp-3", { outcome: "high" })],
        1,
        3,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect({
      keyWarnings: warned.mock.calls.filter((call) => String(call[0]).includes("key")),
      rows: rows().length,
    }).toEqual({ keyWarnings: [], rows: 3 });
    warned.mockRestore();
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => record(`hsp-${n}`));
    const { requests } = await renderHsp(page(fiveOfSeven, 1, 7), {
      [SECOND_PAGE]: page([record("hsp-6", { outcome: "high" }), record("hsp-7")], 6, 7),
    });
    await screen.findByText("Showing 1 to 5 of 7 records");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "HSP History pages" })).getByRole("button", {
        name: "Page 2",
      }),
    );

    expect(await screen.findByText("Showing 6 to 7 of 7 records")).toBeInTheDocument();
    expect({ reads: hspReads(requests()), rows: rows().map((row) => row[0]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["HIGH", "LOW"],
    });
  });

  // What the patient in context decides — a failed read, a read still
  // loading — is `ForPatientInContext`'s (for-patient-in-context.test.tsx),
  // and what every history shares — a history's own 404, the range on a
  // single page, a new filter or sort read from the first page —
  // `FilteredHistory`'s (filtered-history.test.tsx); held here are the page
  // around them, the history, the copy, the columns and the filters this page
  // hands them.
  it("reads the HSP Records only once the patient in context has been read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/hsp", CLINICIAN, {
      [FIRST_PAGE]: page(THREE_RECORDS, 1, 3),
      [TAN_WEI_MING_READ]: patient.answer,
    });
    const before = hspReads(requests());

    await patient.settle(json(TAN_WEI_MING));

    expect(await screen.findByText("Showing 1 to 3 of 3 records")).toBeInTheDocument();
    expect({ after: hspReads(requests()), before }).toEqual({ after: [FIRST_PAGE], before: [] });
  });

  it("says it couldn't load HSP records when the source fails, offering Try again", async () => {
    await renderHsp(bare(502));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load HSP records$/);
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });
});

// The Mandatory elements of an HSP Record are its Outcome and Status
// (/docs/domain/hsp.md): a row missing one, or with one in the wrong format,
// carries a warning, and is shown in full. How the warning is drawn and read
// is the shared module's (mandatory-elements.test.tsx).
describe("HSP, a Mandatory element missing", () => {
  it.each([
    ["Outcome", "outcome"],
    ["Status", "status"],
  ] as const)("warns on a row whose %s is missing, naming its column", async (label, element) => {
    await renderHsp(page([record("hsp-1", { [element]: null })], 1, 1));
    await screen.findByRole("table", { name: "HSP History" });

    expect(warnings(table())).toEqual([`Critical information missing: ${label}`]);
  });

  it.each([
    ["Outcome", "outcome"],
    ["Status", "status"],
  ] as const)(
    "warns on a row whose %s is in the wrong format, naming its column",
    async (label, element) => {
      await renderHsp(page([record("hsp-1", { [element]: null, invalid: [element] })], 1, 1));
      await screen.findByRole("table", { name: "HSP History" });

      expect(warnings(table())).toEqual([`Critical information missing: ${label}`]);
    },
  );

  it("leads the row with the warning, naming every affected column in the table's order", async () => {
    await renderHsp(
      page(
        [record("hsp-1", { invalid: ["startDate"], outcome: null, startDate: null, status: null })],
        1,
        1,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect({
      firstHeading: within(table()).getAllByRole("columnheader")[0]!.textContent,
      warnings: warnings(table()),
    }).toEqual({
      firstHeading: "Missing information",
      warnings: ["Critical information missing: Outcome, Status"],
    });
  });

  // A code outside its closed set is a present value (ADR 0020, D2).
  it("does not warn on a row whose codes are outside their sets", async () => {
    await renderHsp(page([record("hsp-1", { outcome: "critical", status: "expired" })], 1, 1));
    await screen.findByRole("table", { name: "HSP History" });

    expect(warnings(table())).toEqual([null]);
  });

  it("does not warn on a row missing, or holding in the wrong format, only other elements", async () => {
    await renderHsp(
      page(
        [
          record("hsp-1", { endDate: null, startDate: null, submittingMc: null }),
          record(null, {
            endDate: null,
            invalid: ["id", "startDate", "endDate", "submittingMc"],
            startDate: null,
            submittingMc: null,
          }),
          record("hsp-3", { status: null }),
        ],
        1,
        3,
      ),
    );
    await screen.findByRole("table", { name: "HSP History" });

    expect(warnings(table())).toEqual([null, null, "Critical information missing: Status"]);
  });

  it("shows no leading column on a page where no row warns", async () => {
    await renderHsp(page([record("hsp-1", { endDate: null }), record("hsp-2")], 1, 2));
    await screen.findByRole("table", { name: "HSP History" });

    expect(leadingColumn(table())).toEqual({ firstCells: ["LOW", "LOW"], heading: null });
  });
});

/** The read of Tan Wei Ming's history from `startIndex`, with `asked` — filters, a sort — before the page. */
const historyPage = (startIndex: number, asked = "") =>
  `GET /api/patient-hsp/eq081-VQEgP8drUUqCWzHfw3?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five HSP Records of seven, the first page of a history spanning two. */
const FIVE_OF_SEVEN = page(
  ["1", "2", "3", "4", "5"].map((n) => record(`hsp-${n}`)),
  1,
  7,
);

const TWO_OF_SEVEN = page([record("hsp-6"), record("hsp-7")], 6, 7);

/** The table's column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) => within(table()).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the history to its second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText("Showing 1 to 5 of 7 records");
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText("Showing 6 to 7 of 7 records");
}

describe("HSP, sorted", () => {
  it("offers a sort on Start Date and End Date alone, neither sorted at first", async () => {
    await renderHsp(page(THREE_RECORDS, 1, 3));
    await screen.findByRole("table", { name: "HSP History" });

    expect(
      within(table())
        .getAllByRole("columnheader")
        .map((header) => [
          header.textContent,
          header.getAttribute("aria-sort"),
          within(header).queryByRole("button") !== null,
        ]),
    ).toEqual([
      ["Outcome", null, false],
      ["Start Date", "none", true],
      ["End Date", "none", true],
      ["Submitting MC", null, false],
      ["Status", null, false],
    ]);
  });

  // The press cycle is the sort heading's (sort-heading.test.tsx), the
  // parameters HSP_HISTORY's (hsp-api.test.ts): here, each heading's wiring.
  it.each([
    ["Start Date", "startDate"],
    ["End Date", "endDate"],
  ] as const)("sorts by %s, ascending, from the first page", async (heading, by) => {
    const { requests } = await renderHsp(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIVE_OF_SEVEN,
    });
    await toSecondPage();

    await userEvent.click(sortButton(heading));
    await screen.findByText("Showing 1 to 5 of 7 records");

    expect({
      direction: columnHeader(heading).getAttribute("aria-sort"),
      reads: hspReads(requests()),
    }).toEqual({
      direction: "ascending",
      reads: [FIRST_PAGE, SECOND_PAGE, historyPage(1, `sortBy=${by}&sortOrder=ascending`)],
    });
  });

  it("holds the page, busy, and both sort headings still while a read is pending", async () => {
    const next = deferred();
    await renderHsp(FIVE_OF_SEVEN, { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 7 records");

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect({
      busy: table().closest("[aria-busy]")?.getAttribute("aria-busy"),
      rows: rows().length,
      sortsDisabled: [sortButton("Start Date"), sortButton("End Date")].map((button) =>
        button.hasAttribute("disabled"),
      ),
    }).toEqual({ busy: "true", rows: 5, sortsDisabled: [true, true] });
    await next.settle(TWO_OF_SEVEN);
  });
});

type User = ReturnType<typeof userEvent.setup>;

/** The button that opens the filters, whatever it says is set. */
const filterTrigger = () => screen.getByRole("button", { name: /^Filter/ });

/** Opens the filters, unless they are open already. */
async function openFilters(user: User) {
  if (filterTrigger().getAttribute("aria-expanded") !== "true") await user.click(filterTrigger());
}

const filterPanel = () => screen.getByRole("form", { name: "HSP filters" });

/** A multi-select's trigger in the open panel, whatever it says is selected. */
const pickerTrigger = (name: string) =>
  within(filterPanel()).getByRole("button", {
    // `name` is only ever one of this file's literal picker names.
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
    name: new RegExp(`^${name}: `),
  });

/** Opens the filters and the picker `name`, toggles each of `labels`, and closes the picker. */
async function pick(user: User, name: string, ...labels: string[]) {
  await openFilters(user);
  await user.click(pickerTrigger(name));
  for (const label of labels) await user.click(screen.getByRole("option", { name: label }));
  await user.keyboard("{Escape}");
}

/** A date range's field in the open panel: its group, then its end. */
const dateField = (range: "Start Date" | "End Date", end: "From" | "To") =>
  within(within(filterPanel()).getByRole("group", { name: range })).getByLabelText(
    end,
  ) as HTMLInputElement;

/** Sets a date field of the open panel as a date picker does: a whole `YYYY-MM-DD` or nothing. */
const setDate = (range: "Start Date" | "End Date", end: "From" | "To", value: string) =>
  fireEvent.change(dateField(range, end), { target: { value } });

async function apply(user: User) {
  await openFilters(user);
  await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));
}

// Reopening on the filters applied, Reset, the refusal of an inverted range
// and a filter matching none are the shared panel's and the filtered
// history's (history-filter-popover.test.tsx, filtered-history.test.tsx);
// which ranges are checked is HSP_DATE_COLUMNS' (hsp-api.test.ts).
describe("HSP, filtered", () => {
  it("offers the five filters: two multi-selects, two date ranges and the Status, All by default", async () => {
    const user = userEvent.setup();
    await renderHsp(page(THREE_RECORDS, 1, 3));
    await screen.findByRole("table", { name: "HSP History" });

    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      dates: (["Start Date", "End Date"] as const).flatMap((range) =>
        (["From", "To"] as const).map((end) => [
          range,
          end,
          dateField(range, end).type,
          dateField(range, end).value,
        ]),
      ),
      pickers: within(filterPanel())
        .getAllByRole("button", { name: /: / })
        .map((trigger) => trigger.textContent),
      statuses: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => [choice.textContent, choice.getAttribute("aria-checked")]),
    }).toEqual({
      count: "Filter",
      dates: [
        ["Start Date", "From", "date", ""],
        ["Start Date", "To", "date", ""],
        ["End Date", "From", "date", ""],
        ["End Date", "To", "date", ""],
      ],
      pickers: ["Outcome: Any", "Submitting MC: Any"],
      statuses: [
        ["All", "true"],
        ["Active", "false"],
        ["Inactive", "false"],
      ],
    });
  });

  it("lists each closed set by its labels, in the set's order, its search named for its column", async () => {
    const user = userEvent.setup();
    await renderHsp(page(THREE_RECORDS, 1, 3));
    await screen.findByRole("table", { name: "HSP History" });
    await openFilters(user);

    const listed: string[][] = [];
    for (const [name, plural] of [
      ["Outcome", "Outcomes"],
      ["Submitting MC", "Submitting MCs"],
    ] as const) {
      await user.click(pickerTrigger(name));
      listed.push([
        screen.getByRole("combobox", { name: plural }).getAttribute("placeholder") ?? "",
        ...screen.getAllByRole("option").map((option) => option.textContent),
      ]);
      await user.keyboard("{Escape}");
    }

    expect(listed).toEqual([
      ["Search Outcomes", "LOW", "MODERATE", "HIGH"],
      [
        "Search Submitting MCs",
        "Nee Soon Camp MC",
        "Kranji Camp MC",
        "Hendon Camp MC",
        "Mandai Hill Camp MC",
        "Khatib Camp MC",
      ],
    ]);
  });

  it.each([
    ["Outcome", ["HIGH", "LOW"], "outcome=low&outcome=high"],
    [
      "Submitting MC",
      ["Khatib Camp MC", "Hendon Camp MC"],
      "submittingMc=hendon-camp&submittingMc=khatib-camp",
    ],
  ] as const)(
    "filters by %s, each code chosen once, counted as one, from the first page, and closes",
    async (name, labels, asked) => {
      const user = userEvent.setup();
      const { requests } = await renderHsp(FIVE_OF_SEVEN, {
        [SECOND_PAGE]: TWO_OF_SEVEN,
        [historyPage(1, asked)]: page([record("only")], 1, 1),
      });
      await toSecondPage();

      await pick(user, name, ...labels);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 record");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        open: screen.queryByRole("form", { name: "HSP filters" }),
        read: hspReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 1 set", open: null, read: historyPage(1, asked) });
    },
  );

  it.each([
    ["Start Date", "startDate", "2023-01-01", "2023-12-31"],
    ["End Date", "endDate", "2024-01-01", "2024-12-31"],
  ] as const)("filters by a %s range, and counts each end", async (range, field, from, to) => {
    const user = userEvent.setup();
    const asked = `${field}From=${from}&${field}To=${to}`;
    const { requests } = await renderHsp(page(THREE_RECORDS, 1, 3), {
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "HSP History" });

    await openFilters(user);
    setDate(range, "From", from);
    setDate(range, "To", to);
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 record");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: hspReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
  });

  // The Status Inactive is sent by the case combining the filters, below.
  it("filters by the Status Active, from the first page", async () => {
    const user = userEvent.setup();
    const { requests } = await renderHsp(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, "status=active")]: page(THREE_RECORDS.slice(0, 1), 1, 1),
    });
    await toSecondPage();

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Active" }));
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 record");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: hspReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 1 set", read: historyPage(1, "status=active") });
  });

  it("combines the filters with AND, and with the current sort", async () => {
    const user = userEvent.setup();
    const asked =
      "outcome=moderate&startDateFrom=2023-01-01&submittingMc=kranji-camp&status=inactive" +
      "&sortBy=endDate&sortOrder=ascending";
    const { requests } = await renderHsp(page(THREE_RECORDS, 1, 3), {
      [historyPage(1, "sortBy=endDate&sortOrder=ascending")]: page(THREE_RECORDS, 1, 3),
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "HSP History" });
    await userEvent.click(sortButton("End Date"));
    // The heading is held still until the sorted read lands.
    await waitFor(() => expect(sortButton("End Date")).toBeEnabled());

    await pick(user, "Outcome", "MODERATE");
    setDate("Start Date", "From", "2023-01-01");
    await pick(user, "Submitting MC", "Kranji Camp MC");
    await user.click(within(filterPanel()).getByRole("radio", { name: "Inactive" }));
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 record");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: hspReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 4 set", read: historyPage(1, asked) });
  });
});
