import { screen, within } from "@testing-library/react";
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
