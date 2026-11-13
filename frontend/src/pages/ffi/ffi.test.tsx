import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which the FFI page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * The reads of Tan Wei Ming's FFI History, as answer-table keys. Written out
 * rather than built from `FFI_HISTORY.path`, so the path is checked against
 * the contract and not against the code under test.
 */
const FIRST_PAGE = "GET /api/patient-ffi/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5";
const SECOND_PAGE = "GET /api/patient-ffi/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5";

/** One FFI as the backend sends it, its fields given by `fields` over a Dental FFI's. */
const ffi = (id: string, fields: Record<string, string> = {}) => ({
  expiryDate: "2024-11-21",
  higherAuthority: "carmo",
  id,
  issueDate: "2023-11-22",
  outcome: "fit",
  status: "active",
  submittingMc: "hendon-camp",
  type: "dental",
  ...fields,
});

/** Tan Wei Ming's four FFIs, as the patient source's mock serves them. */
const FOUR_FFIS = [
  ffi("ffi-tan-wei-ming-1", {
    expiryDate: "2024-10-13",
    higherAuthority: "camo",
    issueDate: "2023-10-14",
    status: "inactive",
    submittingMc: "nee-soon-camp",
    type: "overseas-exercises",
  }),
  ffi("ffi-tan-wei-ming-2", {
    expiryDate: "2025-01-09",
    higherAuthority: "cnmo",
    issueDate: "2024-01-10",
    outcome: "request-waiver",
    submittingMc: "kranji-camp",
    type: "ippt",
  }),
  ffi("ffi-tan-wei-ming-3"),
  ffi("ffi-tan-wei-ming-4", {
    expiryDate: "2024-08-04",
    higherAuthority: "camo",
    issueDate: "2023-08-05",
    outcome: "unfit",
    status: "inactive",
    submittingMc: "mandai-hill-camp",
    type: "vocational",
  }),
];

const page = (ffis: readonly object[], startIndex: number, totalResults: number) =>
  json({ ffis, startIndex, totalResults });

/** The FFI page with Tan Wei Ming in context, his FFI History's first page answered `answer`. */
async function renderFfi(answer: Answers[string], more: Answers = {}) {
  withPatientInContext(TAN_WEI_MING.id);
  return renderSignedIn("/ffi", CLINICIAN, {
    [FIRST_PAGE]: answer,
    [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    ...more,
  });
}

/** The FFI reads among what the page sent. */
const ffiReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient-ffi/"));

const table = () => screen.getByRole("table", { name: "FFI History" });

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

/** The badge in each body row's column `column`, by its text and its variant. */
const badges = (column: number) =>
  within(table())
    .getAllByRole("row")
    .slice(1)
    .map((row) => {
      const badge = within(row).getAllByRole("cell")[column]!.querySelector("[data-slot=badge]");
      return `${badge?.textContent} ${badge?.getAttribute("data-variant")}`;
    });

afterEach(() => {
  clearEhrContext();
});

describe("FFI", () => {
  it("is headed FFI History", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^FFI History$/);
  });

  it("shows the patient in context's FFIs in the source's order, each code by its label", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));

    expect({
      headings: within(await screen.findByRole("table", { name: "FFI History" }))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
    }).toEqual({
      headings: [
        "FFI Type",
        "Outcome",
        "Issue Date",
        "Expiry Date",
        "Higher Authority",
        "Submitting MC",
        "Status",
      ],
      rows: [
        [
          "Overseas Exercises",
          "FIT",
          "14 Oct 2023",
          "13 Oct 2024",
          "CAMO",
          "Nee Soon Camp MC",
          "Inactive",
        ],
        [
          "IPPT FFI",
          "REQUEST WAIVER",
          "10 Jan 2024",
          "09 Jan 2025",
          "CNMO",
          "Kranji Camp MC",
          "Active",
        ],
        ["Dental FFI", "FIT", "22 Nov 2023", "21 Nov 2024", "CARMO", "Hendon Camp MC", "Active"],
        [
          "Vocational FFI",
          "UNFIT",
          "05 Aug 2023",
          "04 Aug 2024",
          "CAMO",
          "Mandai Hill Camp MC",
          "Inactive",
        ],
      ],
    });
  });

  // With the four rows above, every code of every closed set is named.
  it("names the FFI Types and Higher Authorities Tan Wei Ming's FFIs lack by their labels", async () => {
    await renderFfi(
      page(
        [
          ffi("a", { higherAuthority: "comd-mmi", type: "cat-a-ocs-scs" }),
          ffi("b", { higherAuthority: "dis", type: "ord-release-from-service" }),
        ],
        1,
        2,
      ),
    );
    await screen.findByRole("table", { name: "FFI History" });

    expect(rows().map((row) => `${row[0]} / ${row[4]}`)).toEqual([
      "CAT A - OCS/SCS / Comd MMI",
      "ORD / Release from Service / DIS",
    ]);
  });

  it("shows each Outcome as a badge: FIT green, REQUEST WAIVER amber, UNFIT red", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });

    expect(badges(1)).toEqual([
      "FIT success",
      "REQUEST WAIVER warning",
      "FIT success",
      "UNFIT danger",
    ]);
  });

  it("shows each Status as a badge: Active green, Inactive muted", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });

    expect({
      badges: badges(6),
      muted: within(table())
        .getAllByText("Inactive")
        .map((badge) => badge.classList.contains("text-muted-foreground")),
    }).toEqual({
      badges: ["Inactive secondary", "Active success", "Active success", "Inactive secondary"],
      muted: [true, true],
    });
  });

  it("says it is loading FFIs while the first read is in flight", async () => {
    const answer = deferred();
    await renderFfi(answer.answer);

    expect(await screen.findByRole("status")).toHaveTextContent(/^Loading FFIs…$/);
    await answer.settle(page(FOUR_FFIS, 1, 4));
  });

  // Status is the source's: an FFI past its Expiry Date shows the Status the
  // source sent, never one worked out from the date.
  it("shows the source's Status even for an FFI past its Expiry Date", async () => {
    await renderFfi(page([ffi("lapsed", { expiryDate: "2001-02-05", status: "active" })], 1, 1));

    expect(await screen.findByRole("table", { name: "FFI History" })).toBeInTheDocument();
    expect(rows()).toEqual([
      ["Dental FFI", "FIT", "22 Nov 2023", "05 Feb 2001", "CARMO", "Hendon Camp MC", "Active"],
    ]);
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => ffi(`ffi-${n}`));
    const { requests } = await renderFfi(page(fiveOfSeven, 1, 7), {
      [SECOND_PAGE]: page([ffi("ffi-6", { type: "ippt" }), ffi("ffi-7")], 6, 7),
    });
    await screen.findByText("Showing 1 to 5 of 7 FFIs");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "FFI History pages" })).getByRole("button", {
        name: "Page 2",
      }),
    );

    expect(await screen.findByText("Showing 6 to 7 of 7 FFIs")).toBeInTheDocument();
    expect({ reads: ffiReads(requests()), rows: rows().map((row) => row[0]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["IPPT FFI", "Dental FFI"],
    });
  });

  it("says No FFIs for a patient with none", async () => {
    await renderFfi(page([], 1, 0));

    expect(await screen.findByText("No FFIs")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("says No patient in context, and reads nothing, with no patient in context", async () => {
    const { requests } = await renderSignedIn("/ffi", CLINICIAN);

    expect({
      note: screen.getByText("No patient in context"),
      reads: ffiReads(requests()),
    }).toEqual({ note: expect.anything() as unknown, reads: [] });
  });

  it("says No patient in context, and reads no FFIs, when the patient in context reads as none", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/ffi", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_FFIS, 1, 4),
      [TAN_WEI_MING_READ]: bare(404),
    });

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(ffiReads(requests())).toEqual([]);
  });

  it("reads the FFIs only once the patient in context has been read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/ffi", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_FFIS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });
    const before = ffiReads(requests());

    await patient.settle(json(TAN_WEI_MING));

    expect(await screen.findByText("Showing 1 to 4 of 4 FFIs")).toBeInTheDocument();
    expect({ after: ffiReads(requests()), before }).toEqual({ after: [FIRST_PAGE], before: [] });
  });

  it("says it is loading FFIs while the patient in context is read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/ffi", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_FFIS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });

    expect(screen.getByRole("main").querySelector("[role=status]")).toHaveTextContent(
      /^Loading FFIs…$/,
    );
    await patient.settle(json(TAN_WEI_MING));
  });

  it("reads no FFIs, and shows only the patient's failure, when the patient in context cannot be read", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/ffi", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_FFIS, 1, 4),
      [TAN_WEI_MING_READ]: bare(502),
    });

    expect({
      alert: (await screen.findByRole("alert")).textContent,
      noPatient: screen.queryByText("No patient in context"),
      reads: ffiReads(requests()),
    }).toEqual({ alert: "Couldn't load patient", noPatient: null, reads: [] });
  });

  it("says No patient in context for a patient the source holds none under, and reports nothing", async () => {
    await renderFfi(bare(404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says it couldn't load FFIs when the source fails, offering Try again", async () => {
    await renderFfi(bare(502));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load FFIs$/);
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });
});

/** The read of Tan Wei Ming's history from `startIndex`, with `asked` — filters, a sort — before the page. */
const historyPage = (startIndex: number, asked = "") =>
  `GET /api/patient-ffi/eq081-VQEgP8drUUqCWzHfw3?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five FFIs of seven, the first page of a history spanning two. */
const FIVE_OF_SEVEN = page(
  ["1", "2", "3", "4", "5"].map((n) => ffi(`ffi-${n}`)),
  1,
  7,
);

const TWO_OF_SEVEN = page([ffi("ffi-6"), ffi("ffi-7")], 6, 7);

/** The table's column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) => within(table()).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the history to its second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText("Showing 1 to 5 of 7 FFIs");
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText("Showing 6 to 7 of 7 FFIs");
}

describe("FFI, sorted", () => {
  it("offers a sort on Issue Date and Expiry Date alone, neither sorted at first", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });

    expect(
      within(table())
        .getAllByRole("columnheader")
        .map((header) => [
          header.textContent,
          header.getAttribute("aria-sort"),
          within(header).queryByRole("button") !== null,
        ]),
    ).toEqual([
      ["FFI Type", null, false],
      ["Outcome", null, false],
      ["Issue Date", "none", true],
      ["Expiry Date", "none", true],
      ["Higher Authority", null, false],
      ["Submitting MC", null, false],
      ["Status", null, false],
    ]);
  });

  // The press cycle is the sort heading's (sort-heading.test.tsx), the
  // parameters FFI_HISTORY's (ffi-api.test.ts): here, each heading's wiring.
  it.each([
    ["Issue Date", "issueDate"],
    ["Expiry Date", "expiryDate"],
  ] as const)("sorts by %s, ascending, from the first page", async (heading, by) => {
    const { requests } = await renderFfi(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIVE_OF_SEVEN,
    });
    await toSecondPage();

    await userEvent.click(sortButton(heading));
    await screen.findByText("Showing 1 to 5 of 7 FFIs");

    expect({
      direction: columnHeader(heading).getAttribute("aria-sort"),
      reads: ffiReads(requests()),
    }).toEqual({
      direction: "ascending",
      reads: [FIRST_PAGE, SECOND_PAGE, historyPage(1, `sortBy=${by}&sortOrder=ascending`)],
    });
  });

  it("holds the page, busy, and both sort headings still while a read is pending", async () => {
    const next = deferred();
    await renderFfi(FIVE_OF_SEVEN, { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 7 FFIs");

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

const filterPanel = () => screen.getByRole("form", { name: "FFI filters" });

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

describe("FFI, filtered", () => {
  it("offers the seven filters: four multi-selects, two date ranges and the Status, All by default", async () => {
    const user = userEvent.setup();
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });

    await openFilters(user);

    expect({
      dates: (["Issue Date", "Expiry Date"] as const).flatMap((range) =>
        (["From", "To"] as const).map((end) => [
          range,
          end,
          dateField(range, end).type,
          dateField(range, end).getAttribute("aria-invalid"),
        ]),
      ),
      pickers: ["FFI Type", "Outcome", "Higher Authority", "Submitting MC"].map(
        (name) => pickerTrigger(name).textContent,
      ),
      statuses: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => [choice.textContent, choice.getAttribute("aria-checked")]),
    }).toEqual({
      dates: [
        ["Issue Date", "From", "date", "false"],
        ["Issue Date", "To", "date", "false"],
        ["Expiry Date", "From", "date", "false"],
        ["Expiry Date", "To", "date", "false"],
      ],
      pickers: ["FFI Type: Any", "Outcome: Any", "Higher Authority: Any", "Submitting MC: Any"],
      statuses: [
        ["All", "true"],
        ["Active", "false"],
        ["Inactive", "false"],
      ],
    });
  });

  it("lists each closed set by its labels, in the set's order", async () => {
    const user = userEvent.setup();
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });
    await openFilters(user);

    const listed: string[][] = [];
    for (const name of ["FFI Type", "Outcome", "Higher Authority", "Submitting MC"]) {
      await user.click(pickerTrigger(name));
      listed.push(screen.getAllByRole("option").map((option) => option.textContent));
      await user.keyboard("{Escape}");
    }

    expect(listed).toEqual([
      [
        "Overseas Exercises",
        "CAT A - OCS/SCS",
        "IPPT FFI",
        "Dental FFI",
        "ORD / Release from Service",
        "Vocational FFI",
      ],
      ["FIT", "REQUEST WAIVER", "UNFIT"],
      ["CAMO", "CARMO", "CNMO", "Comd MMI", "DIS"],
      ["Nee Soon Camp MC", "Kranji Camp MC", "Hendon Camp MC", "Mandai Hill Camp MC"],
    ]);
  });

  it("names each picker's list and search by its column, and says when the search matches none", async () => {
    const user = userEvent.setup();
    await renderFfi(page(FOUR_FFIS, 1, 4));
    await screen.findByRole("table", { name: "FFI History" });
    await openFilters(user);

    const named: string[][] = [];
    for (const [name, plural] of [
      ["FFI Type", "FFI Types"],
      ["Outcome", "Outcomes"],
      ["Higher Authority", "Higher Authorities"],
      ["Submitting MC", "Submitting MCs"],
    ] as const) {
      await user.click(pickerTrigger(name));
      const search = screen.getByRole("combobox", { name: plural });
      await user.type(search, "zzz");
      named.push([
        search.getAttribute("placeholder") ?? "",
        screen.getByText(/^No .* matches\.$/).textContent,
      ]);
      await user.keyboard("{Escape}");
    }

    expect(named).toEqual([
      ["Search FFI Types", "No FFI Type matches."],
      ["Search Outcomes", "No Outcome matches."],
      ["Search Higher Authorities", "No Higher Authority matches."],
      ["Search Submitting MCs", "No Submitting MC matches."],
    ]);
  });

  it.each([
    ["FFI Type", ["Dental FFI", "IPPT FFI"], "type=ippt&type=dental"],
    ["Outcome", ["UNFIT"], "outcome=unfit"],
    ["Higher Authority", ["DIS", "CAMO"], "higherAuthority=camo&higherAuthority=dis"],
    ["Submitting MC", ["Hendon Camp MC"], "submittingMc=hendon-camp"],
  ] as const)(
    "filters by %s, each code chosen once, counted as one, and closes",
    async (name, labels, asked) => {
      const user = userEvent.setup();
      const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4), {
        [historyPage(1, asked)]: page([ffi("only")], 1, 1),
      });
      await screen.findByRole("table", { name: "FFI History" });

      await pick(user, name, ...labels);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 FFIs");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        open: screen.queryByRole("form", { name: "FFI filters" }),
        read: ffiReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 1 set", open: null, read: historyPage(1, asked) });
    },
  );

  it.each([
    ["Issue Date", "issueDate", "2024-01-01", "2024-12-31"],
    ["Expiry Date", "expiryDate", "2025-01-01", "2025-12-31"],
  ] as const)("filters by an %s range, and counts each end", async (range, field, from, to) => {
    const user = userEvent.setup();
    const asked = `${field}From=${from}&${field}To=${to}`;
    const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, asked)]: page([ffi("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "FFI History" });

    await openFilters(user);
    setDate(range, "From", from);
    setDate(range, "To", to);
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 FFIs");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: ffiReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
  });

  // The Status Inactive is sent by the case combining the filters, below.
  it("filters by the Status Active, from the first page", async () => {
    const user = userEvent.setup();
    const { requests } = await renderFfi(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, "status=active")]: page(FOUR_FFIS, 1, 4),
    });
    await toSecondPage();

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Active" }));
    await apply(user);
    await screen.findByText("Showing 1 to 4 of 4 FFIs");

    expect(ffiReads(requests()).slice(-1)[0]).toBe(historyPage(1, "status=active"));
  });

  it("applies on Enter in a date, as on Apply", async () => {
    const user = userEvent.setup();
    const asked = "issueDateFrom=2024-01-01";
    const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, asked)]: page(FOUR_FFIS, 1, 4),
    });
    await screen.findByRole("table", { name: "FFI History" });

    await openFilters(user);
    setDate("Issue Date", "From", "2024-01-01");
    await user.type(dateField("Issue Date", "From"), "{Enter}");

    await waitFor(() => expect(ffiReads(requests()).slice(-1)[0]).toBe(historyPage(1, asked)));
  });

  it("combines the filters with AND, and with the current sort", async () => {
    const user = userEvent.setup();
    const asked =
      "type=ippt&outcome=fit&expiryDateTo=2025-12-31&status=inactive" +
      "&sortBy=issueDate&sortOrder=ascending";
    const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, "sortBy=issueDate&sortOrder=ascending")]: page(FOUR_FFIS, 1, 4),
      [historyPage(1, asked)]: page([ffi("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "FFI History" });
    await userEvent.click(sortButton("Issue Date"));
    // The heading is held still until the sorted read lands.
    await waitFor(() => expect(sortButton("Issue Date")).toBeEnabled());

    await pick(user, "FFI Type", "IPPT FFI");
    await pick(user, "Outcome", "FIT");
    setDate("Expiry Date", "To", "2025-12-31");
    await user.click(within(filterPanel()).getByRole("radio", { name: "Inactive" }));
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 FFIs");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: ffiReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 4 set", read: historyPage(1, asked) });
  });

  it.each([
    ["Issue Date", "2024-01-02", "2024-01-01"],
    ["Expiry Date", "2025-12-31", "2025-01-01"],
  ] as const)(
    "refuses an %s from-date after its to-date beneath the range, and reads nothing",
    async (range, from, to) => {
      const user = userEvent.setup();
      const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4));
      await screen.findByRole("table", { name: "FFI History" });
      const before = ffiReads(requests()).length;

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
        reads: ffiReads(requests()).length - before,
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
    await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, "outcome=fit")]: page([ffi("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "FFI History" });
    await pick(user, "Outcome", "FIT");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 FFIs");
    await pick(user, "Outcome", "UNFIT");
    await user.click(within(filterPanel()).getByRole("radio", { name: "Active" }));

    await user.keyboard("{Escape}");
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      outcome: pickerTrigger("Outcome").textContent,
      status: within(filterPanel())
        .getByRole("radio", { name: "All" })
        .getAttribute("aria-checked"),
    }).toEqual({ count: "Filter, 1 set", outcome: "Outcome: 1 selected", status: "true" });
  });

  it("clears every filter on Reset, reading the history unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const { requests } = await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, "status=active")]: page([ffi("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "FFI History" });
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Active" }));
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 FFIs");

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("button", { name: "Reset" }));
    await screen.findByText("Showing 1 to 4 of 4 FFIs");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: ffiReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter", read: FIRST_PAGE });
  });

  it("says No FFIs match the filters for a filter matching none, and offers the filters still", async () => {
    const user = userEvent.setup();
    await renderFfi(page(FOUR_FFIS, 1, 4), {
      [historyPage(1, "expiryDateTo=2022-12-31")]: page([], 1, 0),
    });
    await screen.findByRole("table", { name: "FFI History" });

    await openFilters(user);
    setDate("Expiry Date", "To", "2022-12-31");
    await apply(user);

    expect({
      empty: (await screen.findByText(/^No FFIs/)).textContent,
      filter: filterTrigger().getAttribute("aria-label"),
      table: screen.queryByRole("table"),
    }).toEqual({ empty: "No FFIs match the filters", filter: "Filter, 1 set", table: null });
  });
});
