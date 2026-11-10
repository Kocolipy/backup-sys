import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** Any signed-in User: `patient:read` is a baseline Permission. */
const CLINICIAN = holding(["patient:read"]);

const TAN_HUI_MIN = {
  id: "dev-patient-05",
  name: "Tan Hui Min",
  nric: "S8512345A",
  gender: "female",
  birthDate: "1985-02-04",
  lastVisit: "2026-11-02",
};

const TAN_BOON_HUAT = {
  id: "dev-patient-09",
  name: "Tan Boon Huat",
  nric: "S6012345C",
  gender: "male",
  birthDate: "1960-09-30",
  lastVisit: null,
};

const page = (patients: readonly object[], startIndex: number, totalResults: number) =>
  json({ patients, startIndex, totalResults });

const TAN_PAGE_1 = "GET /api/patient-search?q=tan&startIndex=1&count=5";
const TAN_PAGE_2 = "GET /api/patient-search?q=tan&startIndex=6&count=5";

async function renderSearch(answers: Answers = {}) {
  return renderSignedIn("/search", CLINICIAN, answers);
}

const box = () => screen.getByRole("searchbox", { name: "Search by name or NRIC" });

async function search(text: string) {
  await userEvent.clear(box());
  await userEvent.type(box(), `${text}{Enter}`);
}

/** Each body row's cells, as text. */
const rows = () =>
  within(screen.getByRole("table"))
    .getAllByRole("row")
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole("cell")
        .map((cell) => cell.textContent),
    );

describe("Search", () => {
  it("is titled Patient Search, with no subtitle", async () => {
    await renderSearch();

    expect(screen.getByRole("heading", { level: 1 })).toHaveTextContent(/^Patient Search$/);
  });

  it("prompts for a search and reads nothing before the first one", async () => {
    const { requests } = await renderSearch();

    expect({
      prompt: screen.getByText(/^Search for a patient by name or full NRIC\.$/).tagName,
      requests: requests(),
      table: screen.queryByRole("table"),
    }).toEqual({ prompt: "P", requests: [], table: null });
  });

  it("offers one box, placeheld for a name or an NRIC, and nothing beside it", async () => {
    await renderSearch();

    expect({
      describedBy: box().getAttribute("aria-describedby"),
      invalid: box().getAttribute("aria-invalid"),
      notes: within(screen.getByRole("search")).queryAllByRole("paragraph"),
      placeholder: box().getAttribute("placeholder"),
    }).toEqual({
      describedBy: null,
      invalid: "false",
      notes: [],
      placeholder: "Search by name or NRIC",
    });
  });

  it("refuses a query under three characters inline, and reads nothing", async () => {
    const { requests } = await renderSearch();

    await search("ta");

    expect({
      invalid: box().getAttribute("aria-invalid"),
      message: screen.getByText(/^Enter at least 3 characters to search by name\.$/).id,
      requests: requests(),
    }).toEqual({ invalid: "true", message: box().getAttribute("aria-describedby"), requests: [] });
  });

  it("clears the refusal once a search is sent", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });
    await search("ta");

    await search("tan");
    await screen.findByRole("table");

    expect({
      describedBy: box().getAttribute("aria-describedby"),
      message: screen.queryByText(/^Enter at least 3 characters/),
    }).toEqual({ describedBy: null, message: null });
  });

  it("says it is searching while the search is in flight", async () => {
    const answer = deferred();
    await renderSearch({ [TAN_PAGE_1]: answer.answer });

    await search("tan");

    expect(await screen.findByRole("status")).toHaveTextContent(/^Searching for patients…$/);
    await answer.settle(page([TAN_HUI_MIN], 1, 1));
  });

  it("shows the matching Patients under Search Results: name, NRIC, gender, both dates", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN, TAN_BOON_HUAT], 1, 7) });

    await search("tan");

    expect({
      headings: within(screen.getByRole("table"))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: await screen.findAllByRole("row").then(() => rows()),
      title: screen.getByRole("heading", { level: 2, name: "Search Results" }).tagName,
    }).toEqual({
      headings: ["Patient Name", "NRIC", "Gender", "Date of Birth", "Last Visit"],
      rows: [
        ["Tan Hui Min", "S8512345A", "Female", "04 Feb 1985", "02 Nov 2026"],
        ["Tan Boon Huat", "S6012345C", "Male", "30 Sep 1960", "—"],
      ],
      title: "H2",
    });
  });

  it("shows a Patient's gender as a badge", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });

    await search("tan");

    expect((await screen.findByText(/^Female$/)).dataset.slot).toBe("badge");
  });

  it("keeps each Patient's id without showing it", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });

    await search("tan");

    await screen.findByRole("table");
    expect(document.body.textContent).not.toContain("dev-patient-05");
  });

  it("sends the query trimmed, and only on submit", async () => {
    const { requests } = await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });

    await userEvent.type(box(), "  tan  ");
    const beforeSubmit = requests().length;
    await userEvent.click(screen.getByRole("button", { name: "Search" }));
    await screen.findByRole("table");

    expect({ beforeSubmit, sent: requests().map(({ path }) => path) }).toEqual({
      beforeSubmit: 0,
      sent: ["/api/patient-search?q=tan&startIndex=1&count=5"],
    });
  });

  it("names the range shown, five to a page, and pages on with the numbered pager", async () => {
    await renderSearch({
      [TAN_PAGE_1]: page(
        Array.from({ length: 5 }, (_, i) => ({ ...TAN_HUI_MIN, id: `p${i}` })),
        1,
        7,
      ),
      [TAN_PAGE_2]: page([TAN_BOON_HUAT, { ...TAN_HUI_MIN, id: "p6" }], 6, 7),
    });
    await search("tan");
    await screen.findByText(/^Showing 1 to 5 of 7 patients$/);

    await userEvent.click(
      within(screen.getByRole("navigation", { name: "Search result pages" })).getByRole("button", {
        name: "Page 2",
      }),
    );

    expect(await screen.findByText(/^Showing 6 to 7 of 7 patients$/)).toBeInTheDocument();
  });

  it("names the range of a search whose every match fits on one page", async () => {
    await renderSearch({
      "GET /api/patient-search?q=S8512345A&startIndex=1&count=5": page([TAN_HUI_MIN], 1, 1),
    });

    await search("S8512345A");

    expect(await screen.findByText(/^Showing 1 to 1 of 1 patients$/)).toBeInTheDocument();
  });

  it("says no Patients match a search that found none, with no range", async () => {
    await renderSearch({
      "GET /api/patient-search?q=nobody&startIndex=1&count=5": page([], 1, 0),
    });

    await search("nobody");

    expect({
      empty: (await screen.findByText(/^No patients match your search$/)).tagName,
      range: screen.queryByText(/^Showing/),
      table: screen.queryByRole("table"),
    }).toEqual({ empty: "P", range: null, table: null });
  });

  it("reports a failed search on the page's line, with a Retry that searches again", async () => {
    const { answer } = await renderSearch({ [TAN_PAGE_1]: bare(502) });
    await search("tan");
    await screen.findByText(/^Unable to search for patients\. Please try again\.$/);
    // Retry is the one way on: the shared listing's own Try again is not offered beside it.
    expect(screen.queryByRole("button", { name: "Try again" })).toBeNull();

    answer({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });
    await userEvent.click(screen.getByRole("button", { name: "Retry" }));

    expect({
      line: screen.queryByText(/^Unable to search for patients/),
      rows: (await screen.findByRole("table")) && rows().length,
    }).toEqual({ line: null, rows: 1 });
  });

  it("offers no Retry while the search stands", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });

    await search("tan");
    await screen.findByRole("table");

    expect(screen.queryByRole("button", { name: "Retry" })).toBeNull();
  });

  it("reads the first page afresh when the same search is submitted again", async () => {
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: page(
        Array.from({ length: 5 }, (_, i) => ({ ...TAN_HUI_MIN, id: `p${i}` })),
        1,
        7,
      ),
      [TAN_PAGE_2]: page([TAN_BOON_HUAT, { ...TAN_HUI_MIN, id: "p6" }], 6, 7),
    });
    await search("tan");
    await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
    await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
    await screen.findByText(/^Showing 6 to 7 of 7 patients$/);

    await search("tan");

    await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
    expect(requests().map(({ path }) => path)).toEqual([
      "/api/patient-search?q=tan&startIndex=1&count=5",
      "/api/patient-search?q=tan&startIndex=6&count=5",
      "/api/patient-search?q=tan&startIndex=1&count=5",
    ]);
  });

  it("keeps the last results under a refused query", async () => {
    const { requests } = await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });
    await search("tan");
    await screen.findByRole("table");

    await search("t");

    expect({ requests: requests().length, rows: rows().length }).toEqual({ requests: 1, rows: 1 });
  });
});
