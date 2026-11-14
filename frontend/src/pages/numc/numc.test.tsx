import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which the NUMC page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * The reads of Tan Wei Ming's NUMC History, as answer-table keys. Written out
 * rather than built from `NUMC_HISTORY.path`, so the path is checked against
 * the contract and not against the code under test.
 */
const FIRST_PAGE = "GET /api/patient-numc/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5";
const SECOND_PAGE = "GET /api/patient-numc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5";

/** One NUMC Record as the backend sends it, its elements given by `fields` over a Diving Duties one's. */
const record = (id: string, fields: Record<string, string> = {}) => ({
  expiryDate: "2024-11-21",
  id,
  issueDate: "2023-11-22",
  outcome: "fit",
  type: "diving",
  ...fields,
});

/** Tan Wei Ming's four NUMC Records, as the patient source's mock serves them. */
const FOUR_RECORDS = [
  record("numc-tan-wei-ming-1", {
    expiryDate: "2024-10-13",
    issueDate: "2023-10-14",
    type: "hyperbaric-chamber",
  }),
  record("numc-tan-wei-ming-2", {
    expiryDate: "2025-01-09",
    issueDate: "2024-01-10",
    outcome: "waiver-rejected",
    type: "submariner",
  }),
  record("numc-tan-wei-ming-3", { outcome: "waiver-granted" }),
  record("numc-tan-wei-ming-4", {
    expiryDate: "2024-08-04",
    issueDate: "2023-08-05",
    outcome: "unfit",
    type: "hyperbaric-chamber",
  }),
];

const page = (records: readonly object[], startIndex: number, totalResults: number) =>
  json({ records, startIndex, totalResults });

/** The NUMC page with Tan Wei Ming in context, his NUMC History's first page answered `answer`. */
async function renderNumc(answer: Answers[string], more: Answers = {}) {
  withPatientInContext(TAN_WEI_MING.id);
  return renderSignedIn("/numc", CLINICIAN, {
    [FIRST_PAGE]: answer,
    [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    ...more,
  });
}

/** The NUMC reads among what the page sent. */
const numcReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient-numc/"));

const table = () => screen.getByRole("table", { name: "NUMC History" });

/** Each body row's cells, as text. */
const rows = () =>
  within(table())
    .getAllByRole("row")
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    );

/** The Outcome badge in each body row, by its text and its variant. */
const outcomeBadges = () =>
  within(table())
    .getAllByRole("row")
    .slice(1)
    .map((row) => {
      const badge = within(row).getAllByRole("cell")[1]!.querySelector("[data-slot=badge]");
      return `${badge?.textContent} ${badge?.getAttribute("data-variant")}`;
    });

afterEach(() => {
  clearEhrContext();
});

describe("NUMC", () => {
  it("is headed NUMC", async () => {
    await renderNumc(page(FOUR_RECORDS, 1, 4));

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^NUMC$/);
  });

  it("shows the patient in context's NUMC Records in the source's order, each code by its label", async () => {
    await renderNumc(page(FOUR_RECORDS, 1, 4));

    expect({
      headings: within(await screen.findByRole("table", { name: "NUMC History" }))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
    }).toEqual({
      headings: ["Type", "Outcome", "Issue Date", "Expiry Date"],
      rows: [
        ["Hyperbaric Chamber Duties", "FIT", "14 Oct 2023", "13 Oct 2024"],
        ["Submariner Duties", "WAIVER REJECTED", "10 Jan 2024", "09 Jan 2025"],
        ["Diving Duties", "WAIVER GRANTED", "22 Nov 2023", "21 Nov 2024"],
        ["Hyperbaric Chamber Duties", "UNFIT", "05 Aug 2023", "04 Aug 2024"],
      ],
    });
  });

  it("shows each Outcome as a badge: FIT green, UNFIT red, WAIVER GRANTED blue, WAIVER REJECTED muted", async () => {
    await renderNumc(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "NUMC History" });

    expect({
      badges: outcomeBadges(),
      blue: within(table())
        .getByText("WAIVER GRANTED")
        .classList.contains("text-info-soft-foreground"),
      muted: within(table())
        .getByText("WAIVER REJECTED")
        .classList.contains("text-muted-foreground"),
    }).toEqual({
      badges: ["FIT success", "WAIVER REJECTED muted", "WAIVER GRANTED info", "UNFIT danger"],
      blue: true,
      muted: true,
    });
  });

  // Nothing works out "expired": a record past its Expiry Date shows as any other.
  it("shows a record past its Expiry Date as it shows any other", async () => {
    await renderNumc(page([record("lapsed", { expiryDate: "2001-02-05" })], 1, 1));

    expect(await screen.findByRole("table", { name: "NUMC History" })).toBeInTheDocument();
    expect(rows()).toEqual([["Diving Duties", "FIT", "22 Nov 2023", "05 Feb 2001"]]);
  });

  it("says it is loading NUMC records while the first read is in flight", async () => {
    const answer = deferred();
    await renderNumc(answer.answer);

    expect(await screen.findByRole("status")).toHaveTextContent(/^Loading NUMC records…$/);
    await answer.settle(page(FOUR_RECORDS, 1, 4));
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => record(`numc-${n}`));
    const { requests } = await renderNumc(page(fiveOfSeven, 1, 7), {
      [SECOND_PAGE]: page([record("numc-6", { type: "submariner" }), record("numc-7")], 6, 7),
    });
    await screen.findByText("Showing 1 to 5 of 7 NUMC records");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "NUMC History pages" })).getByRole("button", {
        name: "Page 2",
      }),
    );

    expect(await screen.findByText("Showing 6 to 7 of 7 NUMC records")).toBeInTheDocument();
    expect({ reads: numcReads(requests()), rows: rows().map((row) => row[0]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["Submariner Duties", "Diving Duties"],
    });
  });

  it("names the range on a single page too", async () => {
    await renderNumc(page(FOUR_RECORDS, 1, 4));

    expect(await screen.findByText("Showing 1 to 4 of 4 NUMC records")).toBeInTheDocument();
  });

  it("says No NUMC records for a patient with none", async () => {
    await renderNumc(page([], 1, 0));

    expect(await screen.findByText("No NUMC records")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  // What the patient in context decides — none, a 404, a failed read — is
  // `ForPatientInContext`'s (for-patient-in-context.test.tsx); held here are
  // the history and the loading copy this page hands it.
  it("reads the NUMC Records only once the patient in context has been read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/numc", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_RECORDS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });
    const before = numcReads(requests());

    await patient.settle(json(TAN_WEI_MING));

    expect(await screen.findByText("Showing 1 to 4 of 4 NUMC records")).toBeInTheDocument();
    expect({ after: numcReads(requests()), before }).toEqual({ after: [FIRST_PAGE], before: [] });
  });

  it("says it is loading NUMC records while the patient in context is read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/numc", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_RECORDS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });

    expect(screen.getByRole("main").querySelector("[role=status]")).toHaveTextContent(
      /^Loading NUMC records…$/,
    );
    await patient.settle(json(TAN_WEI_MING));
  });

  it("says No patient in context with none in context, and reads no NUMC Records", async () => {
    const { requests } = await renderSignedIn("/numc", CLINICIAN, {});

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(numcReads(requests())).toEqual([]);
  });

  it("says No patient in context for a patient the source holds none under, and reports nothing", async () => {
    await renderNumc(bare(404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says it couldn't load NUMC records when the source fails, offering Try again", async () => {
    await renderNumc(bare(502));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load NUMC records$/);
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });

  it("reads the same page again on Try again", async () => {
    const { requests } = await renderNumc(bare(502));
    await screen.findByRole("alert");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(numcReads(requests())).toEqual([FIRST_PAGE, FIRST_PAGE]);
  });
});

/** The read of Tan Wei Ming's history from `startIndex`, with `asked` — filters, a sort — before the page. */
const historyPage = (startIndex: number, asked = "") =>
  `GET /api/patient-numc/eq081-VQEgP8drUUqCWzHfw3?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five NUMC Records of seven, the first page of a history spanning two. */
const FIVE_OF_SEVEN = page(
  ["1", "2", "3", "4", "5"].map((n) => record(`numc-${n}`)),
  1,
  7,
);

const TWO_OF_SEVEN = page([record("numc-6"), record("numc-7")], 6, 7);

/** The table's column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) => within(table()).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the history to its second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText("Showing 1 to 5 of 7 NUMC records");
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText("Showing 6 to 7 of 7 NUMC records");
}

describe("NUMC, sorted", () => {
  it("offers a sort on Issue Date and Expiry Date alone, neither sorted at first", async () => {
    await renderNumc(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "NUMC History" });

    expect(
      within(table())
        .getAllByRole("columnheader")
        .map((header) => [
          header.textContent,
          header.getAttribute("aria-sort"),
          within(header).queryByRole("button") !== null,
        ]),
    ).toEqual([
      ["Type", null, false],
      ["Outcome", null, false],
      ["Issue Date", "none", true],
      ["Expiry Date", "none", true],
    ]);
  });

  // The press cycle is the sort heading's (sort-heading.test.tsx), the
  // parameters NUMC_HISTORY's (numc-api.test.ts): here, each heading's wiring.
  it.each([
    ["Issue Date", "issueDate"],
    ["Expiry Date", "expiryDate"],
  ] as const)("sorts by %s, ascending, from the first page", async (heading, by) => {
    const { requests } = await renderNumc(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIVE_OF_SEVEN,
    });
    await toSecondPage();

    await userEvent.click(sortButton(heading));
    await screen.findByText("Showing 1 to 5 of 7 NUMC records");

    expect({
      direction: columnHeader(heading).getAttribute("aria-sort"),
      reads: numcReads(requests()),
    }).toEqual({
      direction: "ascending",
      reads: [FIRST_PAGE, SECOND_PAGE, historyPage(1, `sortBy=${by}&sortOrder=ascending`)],
    });
  });

  it("holds the page, busy, and both sort headings still while a read is pending", async () => {
    const next = deferred();
    await renderNumc(FIVE_OF_SEVEN, { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 7 NUMC records");

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect({
      busy: table().closest("[aria-busy]")?.getAttribute("aria-busy"),
      rows: rows().length,
      sortsDisabled: [sortButton("Issue Date"), sortButton("Expiry Date")].map((button) =>
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

const filterPanel = () => screen.getByRole("form", { name: "NUMC filters" });

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
const dateField = (range: "Issue Date" | "Expiry Date", end: "From" | "To") =>
  within(within(filterPanel()).getByRole("group", { name: range })).getByLabelText(
    end,
  ) as HTMLInputElement;

/** Sets a date field of the open panel as a date picker does: a whole `YYYY-MM-DD` or nothing. */
const setDate = (range: "Issue Date" | "Expiry Date", end: "From" | "To", value: string) =>
  fireEvent.change(dateField(range, end), { target: { value } });

async function apply(user: User) {
  await openFilters(user);
  await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));
}

describe("NUMC, filtered", () => {
  it("offers the four filters: two multi-selects and two date ranges, none set", async () => {
    const user = userEvent.setup();
    await renderNumc(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "NUMC History" });

    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      dates: (["Issue Date", "Expiry Date"] as const).flatMap((range) =>
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
    }).toEqual({
      count: "Filter",
      dates: [
        ["Issue Date", "From", "date", ""],
        ["Issue Date", "To", "date", ""],
        ["Expiry Date", "From", "date", ""],
        ["Expiry Date", "To", "date", ""],
      ],
      pickers: ["Type: Any", "Outcome: Any"],
    });
  });

  it("lists each closed set by its labels, in the set's order, its search named for its column", async () => {
    const user = userEvent.setup();
    await renderNumc(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "NUMC History" });
    await openFilters(user);

    const listed: string[][] = [];
    for (const [name, plural] of [
      ["Type", "Types"],
      ["Outcome", "Outcomes"],
    ] as const) {
      await user.click(pickerTrigger(name));
      listed.push([
        screen.getByRole("combobox", { name: plural }).getAttribute("placeholder") ?? "",
        ...screen.getAllByRole("option").map((option) => option.textContent),
      ]);
      await user.keyboard("{Escape}");
    }

    expect(listed).toEqual([
      ["Search Types", "Hyperbaric Chamber Duties", "Submariner Duties", "Diving Duties"],
      ["Search Outcomes", "FIT", "UNFIT", "WAIVER GRANTED", "WAIVER REJECTED"],
    ]);
  });

  it.each([
    ["Type", ["Diving Duties", "Hyperbaric Chamber Duties"], "type=hyperbaric-chamber&type=diving"],
    ["Outcome", ["WAIVER REJECTED", "FIT"], "outcome=fit&outcome=waiver-rejected"],
  ] as const)(
    "filters by %s, each code chosen once, counted as one, from the first page, and closes",
    async (name, labels, asked) => {
      const user = userEvent.setup();
      const { requests } = await renderNumc(FIVE_OF_SEVEN, {
        [SECOND_PAGE]: TWO_OF_SEVEN,
        [historyPage(1, asked)]: page([record("only")], 1, 1),
      });
      await toSecondPage();

      await pick(user, name, ...labels);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 NUMC records");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        open: screen.queryByRole("form", { name: "NUMC filters" }),
        read: numcReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 1 set", open: null, read: historyPage(1, asked) });
    },
  );

  it.each([
    ["Issue Date", "issueDate", "2024-01-01", "2024-12-31"],
    ["Expiry Date", "expiryDate", "2025-01-01", "2025-12-31"],
  ] as const)("filters by an %s range, and counts each end", async (range, field, from, to) => {
    const user = userEvent.setup();
    const asked = `${field}From=${from}&${field}To=${to}`;
    const { requests } = await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "NUMC History" });

    await openFilters(user);
    setDate(range, "From", from);
    setDate(range, "To", to);
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 NUMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: numcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
  });

  it("applies on Enter in a date, as on Apply", async () => {
    const user = userEvent.setup();
    const asked = "expiryDateTo=2024-12-31";
    const { requests } = await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page(FOUR_RECORDS, 1, 4),
    });
    await screen.findByRole("table", { name: "NUMC History" });

    await openFilters(user);
    setDate("Expiry Date", "To", "2024-12-31");
    await user.type(dateField("Expiry Date", "To"), "{Enter}");

    await waitFor(() => expect(numcReads(requests()).slice(-1)[0]).toBe(historyPage(1, asked)));
  });

  it("combines the filters with AND, and with the current sort", async () => {
    const user = userEvent.setup();
    const asked =
      "type=submariner&outcome=unfit&issueDateFrom=2023-01-01" +
      "&sortBy=expiryDate&sortOrder=ascending";
    const { requests } = await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, "sortBy=expiryDate&sortOrder=ascending")]: page(FOUR_RECORDS, 1, 4),
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "NUMC History" });
    await userEvent.click(sortButton("Expiry Date"));
    // The heading is held still until the sorted read lands.
    await waitFor(() => expect(sortButton("Expiry Date")).toBeEnabled());

    await pick(user, "Type", "Submariner Duties");
    await pick(user, "Outcome", "UNFIT");
    setDate("Issue Date", "From", "2023-01-01");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 NUMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: numcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 3 set", read: historyPage(1, asked) });
  });

  it.each([
    ["Issue Date", "2024-01-02", "2024-01-01"],
    ["Expiry Date", "2025-12-31", "2025-01-01"],
  ] as const)(
    "refuses an %s from-date after its to-date beneath the range, and reads nothing",
    async (range, from, to) => {
      const user = userEvent.setup();
      const { requests } = await renderNumc(page(FOUR_RECORDS, 1, 4));
      await screen.findByRole("table", { name: "NUMC History" });
      const before = numcReads(requests()).length;

      await openFilters(user);
      setDate(range, "From", from);
      setDate(range, "To", to);
      await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));

      const refusal = within(within(filterPanel()).getByRole("group", { name: range })).getByText(
        `The ${range} from-date must not be after its to-date.`,
      );
      expect({
        count: filterTrigger().getAttribute("aria-label"),
        describedBy: [dateField(range, "From"), dateField(range, "To")].map((input) =>
          input.getAttribute("aria-describedby"),
        ),
        invalid: [dateField(range, "From"), dateField(range, "To")].map((input) =>
          input.getAttribute("aria-invalid"),
        ),
        reads: numcReads(requests()).length - before,
      }).toEqual({
        count: "Filter",
        describedBy: [refusal.id, refusal.id],
        invalid: ["true", "true"],
        reads: 0,
      });
    },
  );

  it("reopens on the filters applied, forgetting a change never applied once the panel closes", async () => {
    const user = userEvent.setup();
    await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, "outcome=fit")]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "NUMC History" });
    await pick(user, "Outcome", "FIT");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 NUMC records");
    await pick(user, "Type", "Diving Duties");
    setDate("Issue Date", "From", "2024-01-01");

    await user.keyboard("{Escape}");
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      issuedFrom: dateField("Issue Date", "From").value,
      outcome: pickerTrigger("Outcome").textContent,
      type: pickerTrigger("Type").textContent,
    }).toEqual({
      count: "Filter, 1 set",
      issuedFrom: "",
      outcome: "Outcome: 1 selected",
      type: "Type: Any",
    });
  });

  it("clears every filter on Reset, reading the history unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const asked = "type=diving&expiryDateFrom=2024-01-01";
    const { requests } = await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "NUMC History" });
    await pick(user, "Type", "Diving Duties");
    setDate("Expiry Date", "From", "2024-01-01");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 NUMC records");

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("button", { name: "Reset" }));
    await screen.findByText("Showing 1 to 4 of 4 NUMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: numcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter", read: FIRST_PAGE });
  });

  it("says No NUMC records match the filters for a filter matching none, and offers the filters still", async () => {
    const user = userEvent.setup();
    await renderNumc(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, "expiryDateTo=2022-12-31")]: page([], 1, 0),
    });
    await screen.findByRole("table", { name: "NUMC History" });

    await openFilters(user);
    setDate("Expiry Date", "To", "2022-12-31");
    await apply(user);

    expect({
      empty: (await screen.findByText(/^No NUMC records/)).textContent,
      filter: filterTrigger().getAttribute("aria-label"),
      table: screen.queryByRole("table"),
    }).toEqual({
      empty: "No NUMC records match the filters",
      filter: "Filter, 1 set",
      table: null,
    });
  });
});
