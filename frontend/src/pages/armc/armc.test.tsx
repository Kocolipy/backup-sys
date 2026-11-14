import { screen, within } from "@testing-library/react";
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
