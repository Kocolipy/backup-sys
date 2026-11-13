import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ehrContext } from "@/auth/ehr-context";
import { bare, deferred, holding, json, type Answers } from "@/auth/session-adapter.testHelpers";
import {
  patientReads,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
  withPatientInContext,
} from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which Patient Search requires. */
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
  it("reads nothing before the first search", async () => {
    const { requests } = await renderSearch();

    expect({ requests: requests(), table: screen.queryByRole("table") }).toEqual({
      requests: [],
      table: null,
    });
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
    await screen.findByRole("table");

    expect({
      headings: within(screen.getByRole("table"))
        .getAllByRole("columnheader")
        .map((heading) => heading.textContent),
      rows: rows(),
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

  it("shows each gender as a badge, tinting Male blue, Female pink, and leaving any other plain", async () => {
    await renderSearch({
      [TAN_PAGE_1]: page(
        [
          TAN_HUI_MIN,
          TAN_BOON_HUAT,
          { ...TAN_HUI_MIN, id: "dev-patient-11", gender: "other" },
          { ...TAN_HUI_MIN, id: "dev-patient-12", gender: "unknown" },
        ],
        1,
        4,
      ),
    });

    await search("tan");

    const tint = (label: string) => {
      // The pattern is built from test-literal input only: label is always one
      // of the four gender labels passed below.
      // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp
      const badge = screen.getByText(new RegExp(`^${label}$`));
      return [
        badge.dataset.slot,
        ...badge.className.split(" ").filter((name) => /^(?:bg|text)-(?!xs$)/.test(name)),
      ];
    };
    await screen.findByRole("table");
    expect({
      female: tint("Female"),
      male: tint("Male"),
      other: tint("Other"),
      unknown: tint("Unknown"),
    }).toEqual({
      female: ["badge", "bg-female-soft", "text-female-soft-foreground"],
      male: ["badge", "bg-male-soft", "text-male-soft-foreground"],
      other: ["badge", "bg-secondary", "text-secondary-foreground"],
      unknown: ["badge", "bg-secondary", "text-secondary-foreground"],
    });
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

/** The page `startIndex` starts of the `tan` search, with `asked` — a filter — before it. */
const tanPage = (startIndex: number, asked = "") =>
  `GET /api/patient-search?q=tan&${asked === "" ? "" : `${asked}&`}startIndex=${startIndex}&count=5`;

/** Five Patients of seven, the first page of a search spanning two. */
const FIRST_OF_TWO = page(
  Array.from({ length: 5 }, (_, i) => ({ ...TAN_HUI_MIN, id: `p${i}` })),
  1,
  7,
);

const SECOND_OF_TWO = page([TAN_BOON_HUAT, { ...TAN_HUI_MIN, id: "p6" }], 6, 7);

/** The paths of every search sent, in order. */
const searchesSent = (requests: () => readonly { path: string }[]) =>
  requests()
    .map(({ path }) => path)
    .filter((path) => path.startsWith("/api/patient-search?"));

/** The path of the latest search sent. */
const lastSearchSent = (requests: () => readonly { path: string }[]) =>
  searchesSent(requests).slice(-1)[0];

/** Moves the results to their second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText(/^Showing 6 to 7 of 7 patients$/);
}

type User = ReturnType<typeof userEvent.setup>;

/** The button that opens the filters, whatever it says is set. */
const filterTrigger = () => screen.getByRole("button", { name: /^Filter/ });

/** Opens the filters, unless they are open already. */
async function openFilters(user: User) {
  if (filterTrigger().getAttribute("aria-expanded") !== "true") await user.click(filterTrigger());
}

const filterPanel = () => screen.getByRole("form", { name: "Patient filters" });

async function apply(user: User) {
  await openFilters(user);
  await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));
}

/** The page with the `tan` search shown, its Filter button with it. */
async function renderSearched() {
  await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });
  await search("tan");
  await screen.findByRole("table");
}

describe("Search, filtered", () => {
  it("offers the gender, All by default", async () => {
    const user = userEvent.setup();
    await renderSearched();

    await openFilters(user);

    expect({
      genders: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => [choice.textContent, choice.getAttribute("aria-checked")]),
      notes: within(filterPanel()).queryAllByRole("paragraph"),
    }).toEqual({
      genders: [
        ["All", "true"],
        ["Male", "false"],
        ["Female", "false"],
      ],
      notes: [],
    });
  });

  it("reopens on the filter applied, forgetting a change never applied once the panel closes", async () => {
    const user = userEvent.setup();
    await renderSearch({
      [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1),
      [tanPage(1, "gender=male")]: page([TAN_BOON_HUAT], 1, 1),
    });
    await search("tan");
    await screen.findByRole("table");
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Male" }));
    await apply(user);
    await screen.findByText("Tan Boon Huat");
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Female" }));

    await user.keyboard("{Escape}");
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      genders: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => choice.getAttribute("aria-checked")),
    }).toEqual({ count: "Filter, 1 set", genders: ["false", "true", "false"] });
  });

  it("filters by gender from the first page, counts it, and closes", async () => {
    const user = userEvent.setup();
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [TAN_PAGE_2]: SECOND_OF_TWO,
      [tanPage(1, "gender=male")]: page([TAN_BOON_HUAT], 1, 1),
    });
    await search("tan");
    await toSecondPage();

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Male" }));
    await apply(user);
    await screen.findByText(/^Showing 1 to 1 of 1 patients$/);

    expect({
      badge: within(filterTrigger()).getByText("1").dataset.slot,
      count: filterTrigger().getAttribute("aria-label"),
      open: screen.queryByRole("form", { name: "Patient filters" }),
      sent: lastSearchSent(requests),
    }).toEqual({
      badge: "badge",
      count: "Filter, 1 set",
      open: null,
      sent: "/api/patient-search?q=tan&gender=male&startIndex=1&count=5",
    });
  });

  it("clears the filter on Reset, reading the search unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "gender=female")]: FIRST_OF_TWO,
      [tanPage(6, "gender=female")]: SECOND_OF_TWO,
    });
    await search("tan");
    await screen.findByRole("table");
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Female" }));
    await apply(user);
    await toSecondPage();

    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("button", { name: "Reset" }));
    await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
    const sent = lastSearchSent(requests);
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      gender: within(filterPanel())
        .getByRole("radio", { name: "All" })
        .getAttribute("aria-checked"),
      sent,
    }).toEqual({
      count: "Filter",
      gender: "true",
      sent: "/api/patient-search?q=tan&startIndex=1&count=5",
    });
  });
});

/** Tan Wei Ming as a search finds him: the row keeps the id his read is sent for. */
const TAN_WEI_MING_MATCH = {
  id: TAN_WEI_MING.id,
  name: "Tan Wei Ming",
  nric: "S1234567A",
  gender: "male",
  birthDate: "1985-03-12",
  lastVisit: "2026-10-15",
};

/** Tan Hui Min's read, and the R5 `Patient` the backend serves for it. */
const TAN_HUI_MIN_READ = "GET /api/patient/dev-patient-05";

const TAN_HUI_MIN_PATIENT = {
  birthDate: "1985-02-04",
  gender: "female",
  id: "dev-patient-05",
  identifier: [{ system: "https://fhir.synapxe.sg/NamingSystem/nric", value: "S8512345A" }],
  name: [{ family: "Tan", given: ["Hui", "Min"], text: "Tan Hui Min", use: "official" }],
  resourceType: "Patient",
};

/** The search for `tan` finding Tan Wei Ming and Tan Hui Min, and each one's read. */
const SELECTABLE: Answers = {
  [TAN_PAGE_1]: page([TAN_WEI_MING_MATCH, TAN_HUI_MIN], 1, 2),
  [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
  [TAN_HUI_MIN_READ]: json(TAN_HUI_MIN_PATIENT),
};

/** The result row naming `name`, once the results are in. */
async function resultRow(name: string) {
  const table = await screen.findByRole("table");
  return within(table)
    .getAllByRole("row")
    .find((row) => row.textContent?.startsWith(name))!;
}

describe("Search, selecting a result", () => {
  it("puts the Patient of a clicked row into the EHR context and goes to Home, whose Patient card and top bar name them", async () => {
    await renderSearch(SELECTABLE);
    await search("tan");

    await userEvent.click(within(await resultRow("Tan Hui Min")).getByText("S8512345A"));

    const card = await screen.findByRole("region", { name: "Patient" });
    expect({
      card: within(card).getByRole("heading").textContent,
      context: ehrContext(),
      title: screen.getByRole("heading", { level: 1 }).textContent,
      topBar: screen.getByRole("region", { name: "Patient in context" }).textContent,
    }).toEqual({
      card: "Tan Hui Min",
      context: { patient: "dev-patient-05" },
      title: "Profile",
      topBar: "Tan Hui MinS8512345A",
    });
  });

  it.each(["{Enter}", " "])(
    "selects from the keyboard, pressing %s on the Patient Name button",
    async (key) => {
      await renderSearch(SELECTABLE);
      await search("tan");
      within(await resultRow("Tan Hui Min"))
        .getByRole("button", { name: "Tan Hui Min" })
        .focus();

      await userEvent.keyboard(key);

      await screen.findByRole("region", { name: "Patient" });
      expect({
        context: ehrContext(),
        title: screen.getByRole("heading", { level: 1 }).textContent,
      }).toEqual({ context: { patient: "dev-patient-05" }, title: "Profile" });
    },
  );

  it("replaces the patient already in context, with no confirmation", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderSearch(SELECTABLE);
    await search("tan");

    await userEvent.click(within(await resultRow("Tan Hui Min")).getByText("S8512345A"));

    await screen.findByRole("region", { name: "Patient" });
    expect({
      context: ehrContext(),
      topBar: screen.getByRole("region", { name: "Patient in context" }).textContent,
    }).toEqual({ context: { patient: "dev-patient-05" }, topBar: "Tan Hui MinS8512345A" });
  });

  it("reads the patient already in context once, when that Patient is selected again", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSearch(SELECTABLE);
    await screen.findByRole("region", { name: "Patient in context" });
    await search("tan");

    await userEvent.click(within(await resultRow("Tan Wei Ming")).getByText("S1234567A"));
    await screen.findByRole("region", { name: "Patient" });

    expect(patientReads(requests())).toEqual([TAN_WEI_MING_READ]);
  });

  it("shows only the search box on coming back to Search after a selection", async () => {
    await renderSearch(SELECTABLE);
    await search("tan");
    await userEvent.click(within(await resultRow("Tan Hui Min")).getByText("S8512345A"));
    await screen.findByRole("region", { name: "Patient" });

    await userEvent.click(screen.getByRole("link", { name: "Search" }));

    await screen.findByRole("heading", { level: 1, name: "Patient Search" });
    expect({
      box: (box() as HTMLInputElement).value,
      results: screen.queryByRole("heading", { level: 2, name: "Search Results" }),
    }).toEqual({ box: "", results: null });
  });
});
