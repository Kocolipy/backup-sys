import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { without } from "@/lib/decode.testHelpers";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which the ARMC page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * The reads of Tan Wei Ming's ARMC History, as answer-table keys. Written out
 * rather than built from `ARMC_HISTORY.path`, so the path is checked against
 * the contract and not against the code under test.
 */
const FIRST_PAGE = "GET /api/patient-armc/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5";
const SECOND_PAGE = "GET /api/patient-armc/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5";

/** One ARMC Board as the backend sends it, its elements given by `fields` over an ACES FIT one's. */
const board = (id: string, fields: Record<string, string> = {}) => ({
  boardType: "aces",
  endDate: "2024-02-10",
  id,
  medicalOfficer1: "Medical Officer 1",
  medicalOfficer2: "Medical Officer 2",
  outcome: "fit",
  startDate: "2024-01-01",
  ...fields,
});

/** `board(id, fields)` still open: the backend leaves its End Date out. */
const openBoard = (id: string, fields: Record<string, string> = {}) =>
  without(board(id, fields), "endDate");

/** Tan Wei Ming's two ARMC Boards, as the patient source's mock serves them. */
const TWO_BOARDS = [
  board("armc-tan-wei-ming-1"),
  board("armc-tan-wei-ming-2", {
    endDate: "2021-04-02",
    medicalOfficer1: "Medical Officer 3",
    medicalOfficer2: "Medical Officer 4",
    outcome: "unfit",
    startDate: "2020-11-21",
  }),
];

const page = (records: readonly object[], startIndex: number, totalResults: number) =>
  json({ records, startIndex, totalResults });

/** The ARMC page with Tan Wei Ming in context, his ARMC History's first page answered `answer`. */
async function renderArmc(answer: Answers[string], more: Answers = {}) {
  withPatientInContext(TAN_WEI_MING.id);
  return renderSignedIn("/armc", CLINICIAN, {
    [FIRST_PAGE]: answer,
    [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    ...more,
  });
}

/** The ARMC reads among what the page sent. */
const armcReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient-armc/"));

const table = () => screen.getByRole("table", { name: "ARMC History" });

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

describe("ARMC", () => {
  it("is headed ARMC", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^ARMC$/);
  });

  it("shows the patient in context's ARMC Boards in the source's order, each code by its label", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));

    expect({
      headings: within(await screen.findByRole("table", { name: "ARMC History" }))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
    }).toEqual({
      headings: [
        "Board Type",
        "Outcome",
        "Start Date",
        "End Date",
        "Medical Officer #1",
        "Medical Officer #2",
      ],
      rows: [
        ["ACES", "FIT", "01 Jan 2024", "10 Feb 2024", "Medical Officer 1", "Medical Officer 2"],
        ["ACES", "UNFIT", "21 Nov 2020", "02 Apr 2021", "Medical Officer 3", "Medical Officer 4"],
      ],
    });
  });

  it("shows the Board Type in bold", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));
    await screen.findByRole("table", { name: "ARMC History" });

    expect(within(table()).getAllByText("ACES")[0]).toHaveClass("font-semibold");
  });

  it("shows each Outcome as a badge: FIT green, UNFIT red", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));
    await screen.findByRole("table", { name: "ARMC History" });

    expect(outcomeBadges()).toEqual(["FIT success", "UNFIT danger"]);
  });

  it("shows a board still open with — for its End Date", async () => {
    await renderArmc(page([openBoard("armc-open", { startDate: "2025-06-02" })], 1, 1));

    expect(await screen.findByRole("table", { name: "ARMC History" })).toBeInTheDocument();
    expect(rows()).toEqual([
      ["ACES", "FIT", "02 Jun 2025", "—", "Medical Officer 1", "Medical Officer 2"],
    ]);
  });

  it("shows each Medical Officer's name as the source gives it", async () => {
    await renderArmc(
      page(
        [
          board("armc-named", {
            medicalOfficer1: "Dr Nur Aisyah binte Rahman",
            medicalOfficer2: "<b>Lim</b>",
          }),
        ],
        1,
        1,
      ),
    );
    await screen.findByRole("table", { name: "ARMC History" });

    expect(rows()[0]!.slice(4)).toEqual(["Dr Nur Aisyah binte Rahman", "<b>Lim</b>"]);
  });

  it("says it is loading ARMC records while the first read is in flight", async () => {
    const answer = deferred();
    await renderArmc(answer.answer);

    expect(await screen.findByRole("status")).toHaveTextContent(/^Loading ARMC records…$/);
    await answer.settle(page(TWO_BOARDS, 1, 2));
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => board(`armc-${n}`));
    const { requests } = await renderArmc(page(fiveOfSeven, 1, 7), {
      [SECOND_PAGE]: page(
        [
          board("armc-6", { medicalOfficer1: "Dr Rajesh Kumar" }),
          board("armc-7", { medicalOfficer1: "Dr Chen Mei Ling" }),
        ],
        6,
        7,
      ),
    });
    await screen.findByText("Showing 1 to 5 of 7 ARMC records");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "ARMC History pages" })).getByRole("button", {
        name: "Page 2",
      }),
    );

    expect(await screen.findByText("Showing 6 to 7 of 7 ARMC records")).toBeInTheDocument();
    expect({ reads: armcReads(requests()), rows: rows().map((row) => row[4]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["Dr Rajesh Kumar", "Dr Chen Mei Ling"],
    });
  });

  it("names the range on a single page too", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));

    expect(await screen.findByText("Showing 1 to 2 of 2 ARMC records")).toBeInTheDocument();
  });

  it("says No ARMC records for a patient with none", async () => {
    await renderArmc(page([], 1, 0));

    expect(await screen.findByText("No ARMC records")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  // What the patient in context decides — none, a 404, a failed read — is
  // `ForPatientInContext`'s (for-patient-in-context.test.tsx); held here are
  // the history and the loading copy this page hands it.
  it("reads the ARMC Boards only once the patient in context has been read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/armc", CLINICIAN, {
      [FIRST_PAGE]: page(TWO_BOARDS, 1, 2),
      [TAN_WEI_MING_READ]: patient.answer,
    });
    const before = armcReads(requests());

    await patient.settle(json(TAN_WEI_MING));

    expect(await screen.findByText("Showing 1 to 2 of 2 ARMC records")).toBeInTheDocument();
    expect({ after: armcReads(requests()), before }).toEqual({ after: [FIRST_PAGE], before: [] });
  });

  it("says it is loading ARMC records while the patient in context is read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/armc", CLINICIAN, {
      [FIRST_PAGE]: page(TWO_BOARDS, 1, 2),
      [TAN_WEI_MING_READ]: patient.answer,
    });

    expect(screen.getByRole("main").querySelector("[role=status]")).toHaveTextContent(
      /^Loading ARMC records…$/,
    );
    await patient.settle(json(TAN_WEI_MING));
  });

  it("says No patient in context with none in context, and reads no ARMC Boards", async () => {
    const { requests } = await renderSignedIn("/armc", CLINICIAN, {});

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(armcReads(requests())).toEqual([]);
  });

  it("says No patient in context for a patient the source holds none under, and reports nothing", async () => {
    await renderArmc(bare(404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says it couldn't load ARMC records when the source fails, offering Try again", async () => {
    await renderArmc(bare(502));

    expect(await screen.findByRole("alert")).toHaveTextContent(/^Couldn't load ARMC records$/);
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });

  it("reads the same page again on Try again", async () => {
    const { requests } = await renderArmc(bare(502));
    await screen.findByRole("alert");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(armcReads(requests())).toEqual([FIRST_PAGE, FIRST_PAGE]);
  });
});

/** The read of Tan Wei Ming's history from `startIndex`, with `asked` — filters, a sort — before the page. */
const historyPage = (startIndex: number, asked = "") =>
  `GET /api/patient-armc/eq081-VQEgP8drUUqCWzHfw3?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five ARMC Boards of seven, the first page of a history spanning two. */
const FIVE_OF_SEVEN = page(
  ["1", "2", "3", "4", "5"].map((n) => board(`armc-${n}`)),
  1,
  7,
);

const TWO_OF_SEVEN = page([board("armc-6"), board("armc-7")], 6, 7);

/** The table's column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) => within(table()).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the history to its second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText("Showing 1 to 5 of 7 ARMC records");
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText("Showing 6 to 7 of 7 ARMC records");
}

describe("ARMC, sorted", () => {
  it("offers a sort on Start Date and End Date alone, neither sorted at first", async () => {
    await renderArmc(page(TWO_BOARDS, 1, 2));
    await screen.findByRole("table", { name: "ARMC History" });

    expect(
      within(table())
        .getAllByRole("columnheader")
        .map((header) => [
          header.textContent,
          header.getAttribute("aria-sort"),
          within(header).queryByRole("button") !== null,
        ]),
    ).toEqual([
      ["Board Type", null, false],
      ["Outcome", null, false],
      ["Start Date", "none", true],
      ["End Date", "none", true],
      ["Medical Officer #1", null, false],
      ["Medical Officer #2", null, false],
    ]);
  });

  // The press cycle is the sort heading's (sort-heading.test.tsx), the
  // parameters ARMC_HISTORY's (armc-api.test.ts): here, each heading's wiring.
  it.each([
    ["Start Date", "startDate"],
    ["End Date", "endDate"],
  ] as const)("sorts by %s, ascending, from the first page", async (heading, by) => {
    const { requests } = await renderArmc(FIVE_OF_SEVEN, {
      [SECOND_PAGE]: TWO_OF_SEVEN,
      [historyPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIVE_OF_SEVEN,
    });
    await toSecondPage();

    await userEvent.click(sortButton(heading));
    await screen.findByText("Showing 1 to 5 of 7 ARMC records");

    expect({
      direction: columnHeader(heading).getAttribute("aria-sort"),
      reads: armcReads(requests()),
    }).toEqual({
      direction: "ascending",
      reads: [FIRST_PAGE, SECOND_PAGE, historyPage(1, `sortBy=${by}&sortOrder=ascending`)],
    });
  });

  it("sorts descending on a second press, from the first page", async () => {
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, "sortBy=endDate&sortOrder=ascending")]: page(TWO_BOARDS, 1, 2),
      [historyPage(1, "sortBy=endDate&sortOrder=descending")]: page(TWO_BOARDS, 1, 2),
    });
    await screen.findByRole("table", { name: "ARMC History" });

    await userEvent.click(sortButton("End Date"));
    await waitFor(() => expect(sortButton("End Date")).toBeEnabled());
    await userEvent.click(sortButton("End Date"));

    await waitFor(() =>
      expect(armcReads(requests()).slice(-1)[0]).toBe(
        historyPage(1, "sortBy=endDate&sortOrder=descending"),
      ),
    );
    expect(columnHeader("End Date").getAttribute("aria-sort")).toBe("descending");
  });

  it("holds the page, busy, and both sort headings still while a read is pending", async () => {
    const next = deferred();
    await renderArmc(FIVE_OF_SEVEN, { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 7 ARMC records");

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

const filterPanel = () => screen.getByRole("form", { name: "ARMC filters" });

/** Opens the filters and presses the Outcome choice `label`. */
async function chooseOutcome(user: User, label: "All" | "FIT" | "UNFIT") {
  await openFilters(user);
  await user.click(within(filterPanel()).getByRole("radio", { name: label }));
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

describe("ARMC, filtered", () => {
  it("offers the three filters: the Outcome, All by default, and two date ranges, none set", async () => {
    const user = userEvent.setup();
    await renderArmc(page(TWO_BOARDS, 1, 2));
    await screen.findByRole("table", { name: "ARMC History" });

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
      outcomes: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => [choice.textContent, choice.getAttribute("aria-checked")]),
      pickers: within(filterPanel()).queryAllByRole("button", { name: /: / }),
    }).toEqual({
      count: "Filter",
      dates: [
        ["Start Date", "From", "date", ""],
        ["Start Date", "To", "date", ""],
        ["End Date", "From", "date", ""],
        ["End Date", "To", "date", ""],
      ],
      outcomes: [
        ["All", "true"],
        ["FIT", "false"],
        ["UNFIT", "false"],
      ],
      pickers: [],
    });
  });

  it.each([
    ["FIT", "outcome=fit"],
    ["UNFIT", "outcome=unfit"],
  ] as const)(
    "filters by the Outcome %s, counted as one, from the first page, and closes",
    async (label, asked) => {
      const user = userEvent.setup();
      const { requests } = await renderArmc(FIVE_OF_SEVEN, {
        [SECOND_PAGE]: TWO_OF_SEVEN,
        [historyPage(1, asked)]: page([board("only")], 1, 1),
      });
      await toSecondPage();

      await chooseOutcome(user, label);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 ARMC records");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        open: screen.queryByRole("form", { name: "ARMC filters" }),
        read: armcReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 1 set", open: null, read: historyPage(1, asked) });
    },
  );

  it("sends no Outcome for All, counting none", async () => {
    const user = userEvent.setup();
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, "outcome=unfit")]: page([board("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "ARMC History" });
    await chooseOutcome(user, "UNFIT");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 ARMC records");

    await chooseOutcome(user, "All");
    await apply(user);
    await screen.findByText("Showing 1 to 2 of 2 ARMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: armcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter", read: FIRST_PAGE });
  });

  it.each([
    ["Start Date", "startDate", "2023-01-01", "2023-12-31"],
    ["End Date", "endDate", "2024-01-01", "2024-12-31"],
  ] as const)("filters by a %s range, and counts each end", async (range, field, from, to) => {
    const user = userEvent.setup();
    const asked = `${field}From=${from}&${field}To=${to}`;
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, asked)]: page([board("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "ARMC History" });

    await openFilters(user);
    setDate(range, "From", from);
    setDate(range, "To", to);
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 ARMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: armcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
  });

  it("applies on Enter in a date, as on Apply", async () => {
    const user = userEvent.setup();
    const asked = "endDateFrom=2023-01-01";
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, asked)]: page(TWO_BOARDS, 1, 2),
    });
    await screen.findByRole("table", { name: "ARMC History" });

    await openFilters(user);
    setDate("End Date", "From", "2023-01-01");
    await user.type(dateField("End Date", "From"), "{Enter}");

    await waitFor(() => expect(armcReads(requests()).slice(-1)[0]).toBe(historyPage(1, asked)));
  });

  it("combines the filters with AND, and with the current sort", async () => {
    const user = userEvent.setup();
    const asked =
      "outcome=fit&startDateFrom=2023-01-01&endDateTo=2024-12-31" +
      "&sortBy=startDate&sortOrder=ascending";
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, "sortBy=startDate&sortOrder=ascending")]: page(TWO_BOARDS, 1, 2),
      [historyPage(1, asked)]: page([board("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "ARMC History" });
    await userEvent.click(sortButton("Start Date"));
    // The heading is held still until the sorted read lands.
    await waitFor(() => expect(sortButton("Start Date")).toBeEnabled());

    await chooseOutcome(user, "FIT");
    setDate("Start Date", "From", "2023-01-01");
    setDate("End Date", "To", "2024-12-31");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 ARMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: armcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 3 set", read: historyPage(1, asked) });
  });

  it.each([
    ["Start Date", "2023-01-02", "2023-01-01"],
    ["End Date", "2024-12-31", "2024-01-01"],
  ] as const)(
    "refuses a %s from-date after its to-date beneath the range, and reads nothing",
    async (range, from, to) => {
      const user = userEvent.setup();
      const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2));
      await screen.findByRole("table", { name: "ARMC History" });
      const before = armcReads(requests()).length;

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
        reads: armcReads(requests()).length - before,
      }).toEqual({
        count: "Filter",
        describedBy: [refusal.id, refusal.id],
        reads: 0,
      });
    },
  );

  it("reopens on the filters applied, forgetting a change never applied once the panel closes", async () => {
    const user = userEvent.setup();
    await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, "outcome=fit")]: page([board("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "ARMC History" });
    await chooseOutcome(user, "FIT");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 ARMC records");
    await chooseOutcome(user, "UNFIT");
    setDate("Start Date", "From", "2023-01-01");

    await user.keyboard("{Escape}");
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      fit: within(filterPanel()).getByRole("radio", { name: "FIT" }).getAttribute("aria-checked"),
      startedFrom: dateField("Start Date", "From").value,
    }).toEqual({ count: "Filter, 1 set", fit: "true", startedFrom: "" });
  });

  it("clears every filter on Reset, reading the history unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const asked = "outcome=unfit&endDateFrom=2023-01-01";
    const { requests } = await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, asked)]: page([board("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "ARMC History" });
    await chooseOutcome(user, "UNFIT");
    setDate("End Date", "From", "2023-01-01");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 ARMC records");

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("button", { name: "Reset" }));
    await screen.findByText("Showing 1 to 2 of 2 ARMC records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: armcReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter", read: FIRST_PAGE });
  });

  it("says No ARMC records match the filters for a filter matching none, and offers the filters still", async () => {
    const user = userEvent.setup();
    await renderArmc(page(TWO_BOARDS, 1, 2), {
      [historyPage(1, "startDateTo=2019-12-31")]: page([], 1, 0),
    });
    await screen.findByRole("table", { name: "ARMC History" });

    await openFilters(user);
    setDate("Start Date", "To", "2019-12-31");
    await apply(user);

    expect({
      empty: (await screen.findByText(/^No ARMC records/)).textContent,
      filter: filterTrigger().getAttribute("aria-label"),
      table: screen.queryByRole("table"),
    }).toEqual({
      empty: "No ARMC records match the filters",
      filter: "Filter, 1 set",
      table: null,
    });
  });
});
