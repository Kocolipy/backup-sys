import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { without } from "@/lib/decode.testHelpers";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { TAN_WEI_MING, TAN_WEI_MING_READ, withPatientInContext } from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which the Medication page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * The reads of Tan Wei Ming's Medication History, as answer-table keys.
 * Written out rather than built from `MEDICATION_HISTORY.path`, so the path is
 * checked against the contract and not against the code under test.
 */
const FIRST_PAGE = "GET /api/patient-medication/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5";
const SECOND_PAGE = "GET /api/patient-medication/eq081-VQEgP8drUUqCWzHfw3?startIndex=6&count=5";

/**
 * One Medication Record as the backend sends it, its elements given by
 * `fields` over a pending Paracetamol one's, with every optional element but
 * the Step.
 */
const record = (id: string, fields: Record<string, string> = {}) => ({
  dispensary: "nee-soon-camp",
  dispenseStatus: "pending",
  dosage: "750 mg",
  duration: "5 days",
  endDate: "2026-10-22T17:30:00+08:00",
  frequency: "QDS",
  id,
  medication: "Paracetamol",
  prescribingDoctor: "Dr Lim Wei Jie",
  quantity: "30 tablets",
  route: "PO",
  startDate: "2026-10-18T17:30:00+08:00",
  ...fields,
});

/** Tan Wei Ming's first two Medication Records and a dispensed and an uncollected one. */
const FOUR_RECORDS = [
  record("medication-tan-wei-ming-1"),
  record("medication-tan-wei-ming-2", {
    dispensary: "khatib-camp",
    dosage: "300 mg",
    endDate: "2026-10-22T17:31:00+08:00",
    frequency: "TDS",
    medication: "Amoxicillin",
    quantity: "20 tablets",
    startDate: "2026-10-18T17:31:00+08:00",
  }),
  record("medication-tan-wei-ming-4", {
    dispensary: "khatib-camp",
    dispenseStatus: "dispensed",
    dosage: "500 mg",
    duration: "3 days",
    endDate: "2026-10-15T14:30:00+08:00",
    quantity: "20 tablets",
    startDate: "2026-10-13T14:30:00+08:00",
  }),
  record("medication-tan-wei-ming-5", {
    dispensary: "kranji-camp",
    dispenseStatus: "uncollected",
    dosage: "300 mg",
    duration: "3 days",
    endDate: "2025-04-24T10:00:00+08:00",
    frequency: "TDS",
    medication: "Amoxicillin",
    prescribingDoctor: "Dr Nur Aisyah binte Rahman",
    quantity: "20 tablets",
    startDate: "2025-04-22T10:00:00+08:00",
  }),
];

const page = (records: readonly object[], startIndex: number, totalResults: number) =>
  json({ records, startIndex, totalResults });

/** The Medication page with Tan Wei Ming in context, his history's first page answered `answer`. */
async function renderMedication(answer: Answers[string], more: Answers = {}) {
  withPatientInContext(TAN_WEI_MING.id);
  return renderSignedIn("/medication", CLINICIAN, {
    [FIRST_PAGE]: answer,
    [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    ...more,
  });
}

/** The Medication reads among what the page sent. */
const medicationReads = (sent: readonly { method: string; path: string }[]) =>
  sent
    .map(({ method, path }) => `${method} ${path}`)
    .filter((key) => key.startsWith("GET /api/patient-medication/"));

const table = () => screen.getByRole("table", { name: "Medication History" });

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

/** The Dispense Status badge in each body row, by its text and its variant. */
const statusBadges = () =>
  within(table())
    .getAllByRole("row")
    .slice(1)
    .map((row) => {
      const badge = within(row).getAllByRole("cell")[6]!.querySelector("[data-slot=badge]");
      return `${badge?.textContent} ${badge?.getAttribute("data-variant")}`;
    });

afterEach(() => {
  clearEhrContext();
});

describe("Medication", () => {
  it("is headed Medication", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^Medication$/);
  });

  it("shows the patient in context's Medication Records in the source's order, every column", async () => {
    await renderMedication(page(FOUR_RECORDS.slice(0, 2), 1, 2));

    expect({
      headings: within(await screen.findByRole("table", { name: "Medication History" }))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
    }).toEqual({
      headings: [
        "Medication",
        "Dosage",
        "Route",
        "Frequency",
        "Step",
        "Quantity",
        "Dispense Status",
        "Duration",
        "Start Date",
        "End Date",
        "Dispensary",
        "Prescribing Doctor",
      ],
      rows: [
        [
          "Paracetamol",
          "750 mg",
          "PO",
          "QDS",
          "—",
          "30 tablets",
          "PENDING",
          "5 days",
          "18 Oct 2026 17:30",
          "22 Oct 2026 17:30",
          "Nee Soon Camp MC",
          "Dr Lim Wei Jie",
        ],
        [
          "Amoxicillin",
          "300 mg",
          "PO",
          "TDS",
          "—",
          "20 tablets",
          "PENDING",
          "5 days",
          "18 Oct 2026 17:31",
          "22 Oct 2026 17:31",
          "Khatib Camp MC",
          "Dr Lim Wei Jie",
        ],
      ],
    });
  });

  it("shows the Medication in bold", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });

    expect(within(table()).getAllByText("Paracetamol")[0]).toHaveClass("font-semibold");
  });

  it("shows each Dispense Status as a badge: PENDING muted, DISPENSED green, UNCOLLECTED red", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });

    expect(statusBadges()).toEqual([
      "PENDING muted",
      "PENDING muted",
      "DISPENSED success",
      "UNCOLLECTED danger",
    ]);
  });

  it("names each Dispensary by its Medical Centre's label", async () => {
    const centres = ["nee-soon-camp", "kranji-camp", "hendon-camp", "mandai-hill-camp"];
    await renderMedication(
      page(
        centres.map((dispensary) => record(`medication-${dispensary}`, { dispensary })),
        1,
        4,
      ),
    );
    await screen.findByRole("table", { name: "Medication History" });

    expect(rows().map((row) => row[10])).toEqual([
      "Nee Soon Camp MC",
      "Kranji Camp MC",
      "Hendon Camp MC",
      "Mandai Hill Camp MC",
    ]);
  });

  it("shows each date-time in Singapore time, whatever offset it was sent in", async () => {
    await renderMedication(
      page(
        [
          record("medication-utc", {
            endDate: "2026-03-06T02:00:00Z",
            startDate: "2026-03-01T16:30:00-05:00",
          }),
        ],
        1,
        1,
      ),
    );
    await screen.findByRole("table", { name: "Medication History" });

    expect(rows()[0]!.slice(8, 10)).toEqual(["02 Mar 2026 05:30", "06 Mar 2026 10:00"]);
  });

  it("shows — for each optional element the source lacks", async () => {
    const lacking = ["dosage", "route", "frequency", "step", "quantity", "duration"].reduce<object>(
      (fields, field) => without(fields, field),
      record("medication-bare", { medication: "Oral rehydration salts" }),
    );
    await renderMedication(page([lacking], 1, 1));
    await screen.findByRole("table", { name: "Medication History" });

    expect(rows()[0]).toEqual([
      "Oral rehydration salts",
      "—",
      "—",
      "—",
      "—",
      "—",
      "PENDING",
      "—",
      "18 Oct 2026 17:30",
      "22 Oct 2026 17:30",
      "Nee Soon Camp MC",
      "Dr Lim Wei Jie",
    ]);
  });

  it("shows free text exactly as the source gives it", async () => {
    await renderMedication(
      page([record("medication-text", { duration: "<b>about a week</b>", step: "Step 2" })], 1, 1),
    );
    await screen.findByRole("table", { name: "Medication History" });

    expect([rows()[0]![4], rows()[0]![7]]).toEqual(["Step 2", "<b>about a week</b>"]);
  });

  // The table is wider than a card: its rows stay on one line each and the
  // table scrolls inside the card rather than squeezing its columns.
  it("keeps every cell on one line, inside a container that scrolls sideways", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });

    expect({
      cells: within(table())
        .getAllByRole("cell")
        .every((cell) => cell.classList.contains("whitespace-nowrap")),
      container: table().parentElement?.classList.contains("overflow-x-auto"),
    }).toEqual({ cells: true, container: true });
  });

  it("says it is loading medication records while the first read is in flight", async () => {
    const answer = deferred();
    await renderMedication(answer.answer);

    expect(await screen.findByRole("status")).toHaveTextContent(/^Loading medication records…$/);
    await answer.settle(page(FOUR_RECORDS, 1, 4));
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSix = ["1", "2", "3", "4", "5"].map((n) => record(`medication-${n}`));
    const { requests } = await renderMedication(page(fiveOfSix, 1, 6), {
      [SECOND_PAGE]: page([record("medication-6", { medication: "Prednisolone" })], 6, 6),
    });
    await screen.findByText("Showing 1 to 5 of 6 medication records");

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "Medication History pages" })).getByRole(
        "button",
        { name: "Page 2" },
      ),
    );

    expect(await screen.findByText("Showing 6 to 6 of 6 medication records")).toBeInTheDocument();
    expect({ reads: medicationReads(requests()), rows: rows().map((row) => row[0]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["Prednisolone"],
    });
  });

  it("names the range on a single page too", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));

    expect(await screen.findByText("Showing 1 to 4 of 4 medication records")).toBeInTheDocument();
  });

  it("says No medication records for a patient with none", async () => {
    await renderMedication(page([], 1, 0));

    expect(await screen.findByText("No medication records")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  // What the patient in context decides — none, a 404, a failed read — is
  // `ForPatientInContext`'s (for-patient-in-context.test.tsx); held here are
  // the history and the loading copy this page hands it.
  it("reads the Medication Records only once the patient in context has been read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn("/medication", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_RECORDS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });
    const before = medicationReads(requests());

    await patient.settle(json(TAN_WEI_MING));

    expect(await screen.findByText("Showing 1 to 4 of 4 medication records")).toBeInTheDocument();
    expect({ after: medicationReads(requests()), before }).toEqual({
      after: [FIRST_PAGE],
      before: [],
    });
  });

  it("says it is loading medication records while the patient in context is read", async () => {
    const patient = deferred();
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn("/medication", CLINICIAN, {
      [FIRST_PAGE]: page(FOUR_RECORDS, 1, 4),
      [TAN_WEI_MING_READ]: patient.answer,
    });

    expect(screen.getByRole("main").querySelector("[role=status]")).toHaveTextContent(
      /^Loading medication records…$/,
    );
    await patient.settle(json(TAN_WEI_MING));
  });

  it("says No patient in context with none in context, and reads no Medication Records", async () => {
    const { requests } = await renderSignedIn("/medication", CLINICIAN, {});

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(medicationReads(requests())).toEqual([]);
  });

  it("says No patient in context for a patient the source holds none under, and reports nothing", async () => {
    await renderMedication(bare(404));

    expect(await screen.findByText("No patient in context")).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("says it couldn't load medication records when the source fails, offering Try again", async () => {
    await renderMedication(bare(502));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /^Couldn't load medication records$/,
    );
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByText("No patient in context")).not.toBeInTheDocument();
  });

  it("reads the same page again on Try again", async () => {
    const { requests } = await renderMedication(bare(502));
    await screen.findByRole("alert");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(medicationReads(requests())).toEqual([FIRST_PAGE, FIRST_PAGE]);
  });
});

/** The read of Tan Wei Ming's history from `startIndex`, with `asked` — filters, a sort — before the page. */
const historyPage = (startIndex: number, asked = "") =>
  `GET /api/patient-medication/eq081-VQEgP8drUUqCWzHfw3?${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five Medication Records of six, the first page of a history spanning two. */
const FIVE_OF_SIX = page(
  ["1", "2", "3", "4", "5"].map((n) => record(`medication-${n}`)),
  1,
  6,
);

const ONE_OF_SIX = page([record("medication-6")], 6, 6);

/** The table's column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) => within(table()).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the history to its second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText("Showing 1 to 5 of 6 medication records");
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText("Showing 6 to 6 of 6 medication records");
}

describe("Medication, sorted", () => {
  it("offers a sort on Start Date and End Date alone, neither sorted at first", async () => {
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });

    expect(
      within(table())
        .getAllByRole("columnheader")
        .map((header) => [
          header.textContent,
          header.getAttribute("aria-sort"),
          within(header).queryByRole("button") !== null,
        ]),
    ).toEqual([
      ["Medication", null, false],
      ["Dosage", null, false],
      ["Route", null, false],
      ["Frequency", null, false],
      ["Step", null, false],
      ["Quantity", null, false],
      ["Dispense Status", null, false],
      ["Duration", null, false],
      ["Start Date", "none", true],
      ["End Date", "none", true],
      ["Dispensary", null, false],
      ["Prescribing Doctor", null, false],
    ]);
  });

  // The press cycle is the sort heading's (sort-heading.test.tsx), the
  // parameters MEDICATION_HISTORY's (medication-api.test.ts): here, each
  // heading's wiring.
  it.each([
    ["Start Date", "startDate"],
    ["End Date", "endDate"],
  ] as const)("sorts by %s, ascending, from the first page", async (heading, by) => {
    const { requests } = await renderMedication(FIVE_OF_SIX, {
      [SECOND_PAGE]: ONE_OF_SIX,
      [historyPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIVE_OF_SIX,
    });
    await toSecondPage();

    await userEvent.click(sortButton(heading));
    await screen.findByText("Showing 1 to 5 of 6 medication records");

    expect({
      direction: columnHeader(heading).getAttribute("aria-sort"),
      reads: medicationReads(requests()),
    }).toEqual({
      direction: "ascending",
      reads: [FIRST_PAGE, SECOND_PAGE, historyPage(1, `sortBy=${by}&sortOrder=ascending`)],
    });
  });

  it("holds the page, busy, and both sort headings still while a read is pending", async () => {
    const next = deferred();
    await renderMedication(FIVE_OF_SIX, { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 6 medication records");

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect({
      busy: table().closest("[aria-busy]")?.getAttribute("aria-busy"),
      rows: rows().length,
      sortsDisabled: [sortButton("Start Date"), sortButton("End Date")].map((button) =>
        button.hasAttribute("disabled"),
      ),
    }).toEqual({ busy: "true", rows: 5, sortsDisabled: [true, true] });
    await next.settle(ONE_OF_SIX);
  });
});

type User = ReturnType<typeof userEvent.setup>;

/** The button that opens the filters, whatever it says is set. */
const filterTrigger = () => screen.getByRole("button", { name: /^Filter/ });

/** Opens the filters, unless they are open already. */
async function openFilters(user: User) {
  if (filterTrigger().getAttribute("aria-expanded") !== "true") await user.click(filterTrigger());
}

const filterPanel = () => screen.getByRole("form", { name: "Medication filters" });

/** The Dispense Status multi-select's trigger in the open panel, whatever it says is selected. */
const statusPicker = () =>
  within(filterPanel()).getByRole("button", { name: /^Dispense Status: / });

/** Opens the filters and the Dispense Status picker, toggles each of `labels`, and closes the picker. */
async function pickStatuses(user: User, ...labels: string[]) {
  await openFilters(user);
  await user.click(statusPicker());
  for (const label of labels) await user.click(screen.getByRole("option", { name: label }));
  await user.keyboard("{Escape}");
}

/** A date-time range's field in the open panel: its group, then its end. */
const dateField = (range: "Start Date" | "End Date", end: "From" | "To") =>
  within(within(filterPanel()).getByRole("group", { name: range })).getByLabelText(
    end,
  ) as HTMLInputElement;

/**
 * Sets a date-time field of the open panel as its picker does: a whole
 * `YYYY-MM-DDTHH:mm` or nothing.
 */
const setDateTime = (range: "Start Date" | "End Date", end: "From" | "To", value: string) =>
  fireEvent.change(dateField(range, end), { target: { value } });

async function apply(user: User) {
  await openFilters(user);
  await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));
}

describe("Medication, filtered", () => {
  it("offers the three filters: a Dispense Status multi-select and two date-time ranges, none set", async () => {
    const user = userEvent.setup();
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });

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
    }).toEqual({
      count: "Filter",
      dates: [
        ["Start Date", "From", "datetime-local", ""],
        ["Start Date", "To", "datetime-local", ""],
        ["End Date", "From", "datetime-local", ""],
        ["End Date", "To", "datetime-local", ""],
      ],
      pickers: ["Dispense Status: Any"],
    });
  });

  it("lists the Dispense Statuses by their labels, in the set's order, its search named for its column", async () => {
    const user = userEvent.setup();
    await renderMedication(page(FOUR_RECORDS, 1, 4));
    await screen.findByRole("table", { name: "Medication History" });
    await openFilters(user);

    await user.click(statusPicker());

    expect([
      screen.getByRole("combobox", { name: "Dispense Statuses" }).getAttribute("placeholder"),
      ...screen.getAllByRole("option").map((option) => option.textContent),
    ]).toEqual(["Search Dispense Statuses", "PENDING", "DISPENSED", "UNCOLLECTED"]);
  });

  it.each([
    [["PENDING"], "dispenseStatus=pending"],
    [["UNCOLLECTED", "PENDING"], "dispenseStatus=pending&dispenseStatus=uncollected"],
  ] as const)(
    "filters by the Dispense Statuses %j, each code chosen once, counted as one, from the first page, and closes",
    async (labels, asked) => {
      const user = userEvent.setup();
      const { requests } = await renderMedication(FIVE_OF_SIX, {
        [SECOND_PAGE]: ONE_OF_SIX,
        [historyPage(1, asked)]: page([record("only")], 1, 1),
      });
      await toSecondPage();

      await pickStatuses(user, ...labels);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 medication records");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        open: screen.queryByRole("form", { name: "Medication filters" }),
        read: medicationReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 1 set", open: null, read: historyPage(1, asked) });
    },
  );

  it.each([
    ["Start Date", "startDate", "2025-07-14T16:20", "2026-03-01T10:00"],
    ["End Date", "endDate", "2025-01-01T00:00", "2025-12-31T23:59"],
  ] as const)(
    "filters by a date-time range over the %s, to the minute, and counts each end",
    async (range, field, from, to) => {
      const user = userEvent.setup();
      const asked = `${field}From=${from}&${field}To=${to}`;
      const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4), {
        [historyPage(1, asked)]: page([record("only")], 1, 1),
      });
      await screen.findByRole("table", { name: "Medication History" });

      await openFilters(user);
      setDateTime(range, "From", from);
      setDateTime(range, "To", to);
      await apply(user);
      await screen.findByText("Showing 1 to 1 of 1 medication records");

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        read: medicationReads(requests()).slice(-1)[0],
      }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
    },
  );

  // A range of one minute is no refusal: both ends are inclusive.
  it("reads a range of one minute, its from-end equal to its to-end", async () => {
    const user = userEvent.setup();
    const asked = "startDateFrom=2025-07-14T16:20&startDateTo=2025-07-14T16:20";
    const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "Medication History" });

    await openFilters(user);
    setDateTime("Start Date", "From", "2025-07-14T16:20");
    setDateTime("Start Date", "To", "2025-07-14T16:20");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 medication records");

    expect(medicationReads(requests()).slice(-1)[0]).toBe(historyPage(1, asked));
  });

  it("applies on Enter in a date-time, as on Apply", async () => {
    const user = userEvent.setup();
    const asked = "endDateTo=2025-12-31T23:59";
    const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page(FOUR_RECORDS, 1, 4),
    });
    await screen.findByRole("table", { name: "Medication History" });

    await openFilters(user);
    setDateTime("End Date", "To", "2025-12-31T23:59");
    await user.type(dateField("End Date", "To"), "{Enter}");

    await waitFor(() =>
      expect(medicationReads(requests()).slice(-1)[0]).toBe(historyPage(1, asked)),
    );
  });

  it("combines the filters with AND, and with the current sort", async () => {
    const user = userEvent.setup();
    const asked =
      "dispenseStatus=dispensed&startDateFrom=2025-01-01T00:00" +
      "&sortBy=startDate&sortOrder=ascending";
    const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, "sortBy=startDate&sortOrder=ascending")]: page(FOUR_RECORDS, 1, 4),
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "Medication History" });
    await userEvent.click(sortButton("Start Date"));
    // The heading is held still until the sorted read lands.
    await waitFor(() => expect(sortButton("Start Date")).toBeEnabled());

    await pickStatuses(user, "DISPENSED");
    setDateTime("Start Date", "From", "2025-01-01T00:00");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 medication records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: medicationReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter, 2 set", read: historyPage(1, asked) });
  });

  it.each([
    ["Start Date", "2025-07-14T16:21", "2025-07-14T16:20"],
    ["End Date", "2026-01-01T00:00", "2025-12-31T23:59"],
  ] as const)(
    "refuses a from-end after its to-end over the %s beneath the range, and reads nothing",
    async (range, from, to) => {
      const user = userEvent.setup();
      const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4));
      await screen.findByRole("table", { name: "Medication History" });
      const before = medicationReads(requests()).length;

      await openFilters(user);
      setDateTime(range, "From", from);
      setDateTime(range, "To", to);
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
        reads: medicationReads(requests()).length - before,
      }).toEqual({
        count: "Filter",
        describedBy: [refusal.id, refusal.id],
        invalid: ["true", "true"],
        reads: 0,
      });
    },
  );

  it("clears every filter on Reset, reading the history unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const asked = "dispenseStatus=uncollected&endDateFrom=2025-01-01T00:00";
    const { requests } = await renderMedication(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, asked)]: page([record("only")], 1, 1),
    });
    await screen.findByRole("table", { name: "Medication History" });
    await pickStatuses(user, "UNCOLLECTED");
    setDateTime("End Date", "From", "2025-01-01T00:00");
    await apply(user);
    await screen.findByText("Showing 1 to 1 of 1 medication records");

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("button", { name: "Reset" }));
    await screen.findByText("Showing 1 to 4 of 4 medication records");

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      read: medicationReads(requests()).slice(-1)[0],
    }).toEqual({ count: "Filter", read: FIRST_PAGE });
  });

  it("says No medication records match the filters for a filter matching none, and offers the filters still", async () => {
    const user = userEvent.setup();
    await renderMedication(page(FOUR_RECORDS, 1, 4), {
      [historyPage(1, "endDateTo=2023-12-31T23:59")]: page([], 1, 0),
    });
    await screen.findByRole("table", { name: "Medication History" });

    await openFilters(user);
    setDateTime("End Date", "To", "2023-12-31T23:59");
    await apply(user);

    expect({
      empty: (await screen.findByText(/^No medication records/)).textContent,
      filter: filterTrigger().getAttribute("aria-label"),
      table: screen.queryByRole("table"),
    }).toEqual({
      empty: "No medication records match the filters",
      filter: "Filter, 1 set",
      table: null,
    });
  });
});
