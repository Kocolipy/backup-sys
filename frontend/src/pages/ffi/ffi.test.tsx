import { screen, within } from "@testing-library/react";
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

  it("names its pager FFI History pages", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));

    expect(await screen.findByRole("navigation", { name: "FFI History pages" })).toBeVisible();
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

  it("names the range, a page of five holding every row", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));

    expect(await screen.findByText("Showing 1 to 4 of 4 FFIs")).toBeInTheDocument();
  });

  it("reads five a page, and pages to the second through the numbered pager", async () => {
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => ffi(`ffi-${n}`));
    const { requests } = await renderFfi(page(fiveOfSeven, 1, 7), {
      [SECOND_PAGE]: page([ffi("ffi-6", { type: "ippt" }), ffi("ffi-7")], 6, 7),
    });
    await screen.findByText("Showing 1 to 5 of 7 FFIs");

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect(await screen.findByText("Showing 6 to 7 of 7 FFIs")).toBeInTheDocument();
    expect({ reads: ffiReads(requests()), rows: rows().map((row) => row[0]) }).toEqual({
      reads: [FIRST_PAGE, SECOND_PAGE],
      rows: ["IPPT FFI", "Dental FFI"],
    });
  });

  it("holds the page shown while the next one loads", async () => {
    const next = deferred();
    const fiveOfSeven = ["1", "2", "3", "4", "5"].map((n) => ffi(`ffi-${n}`));
    await renderFfi(page(fiveOfSeven, 1, 7), { [SECOND_PAGE]: next.answer });
    await screen.findByText("Showing 1 to 5 of 7 FFIs");

    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));

    expect({
      busy: table().closest("[aria-busy]")?.getAttribute("aria-busy"),
      rows: rows().length,
    }).toEqual({ busy: "true", rows: 5 });
    await next.settle(page([ffi("ffi-6")], 6, 7));
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

  it("reads the same page again on Try again", async () => {
    const { requests } = await renderFfi([bare(502), page(FOUR_FFIS, 1, 4)]);
    await screen.findByRole("alert");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("Showing 1 to 4 of 4 FFIs")).toBeInTheDocument();
    expect(ffiReads(requests())).toEqual([FIRST_PAGE, FIRST_PAGE]);
  });

  it("offers no row action", async () => {
    await renderFfi(page(FOUR_FFIS, 1, 4));

    expect(
      within(await screen.findByRole("table", { name: "FFI History" })).queryAllByRole("button"),
    ).toEqual([]);
  });
});
