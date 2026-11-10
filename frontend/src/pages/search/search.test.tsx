import { fireEvent, screen, waitFor, within } from "@testing-library/react";
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

  it("tints the Male badge blue, the Female badge pink, and leaves any other gender plain", async () => {
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

    const tint = (label: string) =>
      screen
        .getByText(new RegExp(`^${label}$`))
        .className.split(" ")
        .filter((name) => /^(?:bg|text)-(?!xs$)/.test(name));
    await screen.findByRole("table");
    expect({
      female: tint("Female"),
      male: tint("Male"),
      other: tint("Other"),
      unknown: tint("Unknown"),
    }).toEqual({
      female: ["bg-female-soft", "text-female-soft-foreground"],
      male: ["bg-male-soft", "text-male-soft-foreground"],
      other: ["bg-secondary", "text-secondary-foreground"],
      unknown: ["bg-secondary", "text-secondary-foreground"],
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

/** The page `startIndex` starts of the `tan` search, with `asked` — filters, a sort — before it. */
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

/** The results' column heading named `name`, re-read: a new page is a new table. */
const columnHeader = (name: string) =>
  within(screen.getByRole("table")).getByRole("columnheader", { name });

const sortButton = (name: string) => within(columnHeader(name)).getByRole("button", { name });

/** Moves the results to their second page, and waits for it. */
async function toSecondPage() {
  await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
  await userEvent.click(screen.getByRole("button", { name: "Page 2" }));
  await screen.findByText(/^Showing 6 to 7 of 7 patients$/);
}

describe("Search, sorted", () => {
  it("offers a sort on Date of Birth and Last Visit alone, neither sorted at first", async () => {
    await renderSearch({ [TAN_PAGE_1]: page([TAN_HUI_MIN], 1, 1) });

    await search("tan");
    await screen.findByRole("table");

    expect(
      within(screen.getByRole("table"))
        .getAllByRole("columnheader")
        .map((header) => [header.textContent, header.getAttribute("aria-sort")]),
    ).toEqual([
      ["Patient Name", null],
      ["NRIC", null],
      ["Gender", null],
      ["Date of Birth", "none"],
      ["Last Visit", "none"],
    ]);
  });

  it.each([
    ["Date of Birth", "birthDate"],
    ["Last Visit", "lastVisit"],
  ] as const)(
    "sorts by %s from the first page: ascending, descending, then the default order",
    async (heading, by) => {
      const { requests } = await renderSearch({
        [TAN_PAGE_1]: FIRST_OF_TWO,
        [TAN_PAGE_2]: SECOND_OF_TWO,
        [tanPage(1, `sortBy=${by}&sortOrder=ascending`)]: FIRST_OF_TWO,
        [tanPage(1, `sortBy=${by}&sortOrder=descending`)]: FIRST_OF_TWO,
      });
      await search("tan");
      await toSecondPage();

      const directions: (string | null)[] = [];
      for (const order of ["ascending", "descending", "none"]) {
        await userEvent.click(sortButton(heading));
        await waitFor(() => expect(columnHeader(heading)).toHaveAttribute("aria-sort", order));
        await screen.findByText(/^Showing 1 to 5 of 7 patients$/);
        directions.push(columnHeader(heading).getAttribute("aria-sort"));
      }

      expect({ directions, sent: searchesSent(requests) }).toEqual({
        directions: ["ascending", "descending", "none"],
        sent: [
          "/api/patient-search?q=tan&startIndex=1&count=5",
          "/api/patient-search?q=tan&startIndex=6&count=5",
          `/api/patient-search?q=tan&sortBy=${by}&sortOrder=ascending&startIndex=1&count=5`,
          `/api/patient-search?q=tan&sortBy=${by}&sortOrder=descending&startIndex=1&count=5`,
          "/api/patient-search?q=tan&startIndex=1&count=5",
        ],
      });
    },
  );

  it("sorts by one column at a time, another replacing the sort", async () => {
    await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "sortBy=birthDate&sortOrder=ascending")]: FIRST_OF_TWO,
      [tanPage(1, "sortBy=lastVisit&sortOrder=ascending")]: FIRST_OF_TWO,
    });
    await search("tan");
    await screen.findByRole("table");
    await userEvent.click(sortButton("Date of Birth"));
    await waitFor(() =>
      expect(columnHeader("Date of Birth")).toHaveAttribute("aria-sort", "ascending"),
    );

    await userEvent.click(sortButton("Last Visit"));
    await waitFor(() =>
      expect(columnHeader("Last Visit")).toHaveAttribute("aria-sort", "ascending"),
    );

    expect(columnHeader("Date of Birth")).toHaveAttribute("aria-sort", "none");
  });

  it("keeps the sort for a new search, read from its first page", async () => {
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "sortBy=birthDate&sortOrder=ascending")]: FIRST_OF_TWO,
      "GET /api/patient-search?q=lee&sortBy=birthDate&sortOrder=ascending&startIndex=1&count=5":
        page([TAN_HUI_MIN], 1, 1),
    });
    await search("tan");
    await screen.findByRole("table");
    await userEvent.click(sortButton("Date of Birth"));
    await screen.findByText(/^Showing 1 to 5 of 7 patients$/);

    await search("lee");
    await screen.findByText(/^Showing 1 to 1 of 1 patients$/);

    expect(lastSearchSent(requests)).toBe(
      "/api/patient-search?q=lee&sortBy=birthDate&sortOrder=ascending&startIndex=1&count=5",
    );
  });
});

type User = ReturnType<typeof userEvent.setup>;

/** The button that opens the filters, whatever it says is set. */
const filterTrigger = () => screen.getByRole("button", { name: /^Filter/ });

/** Opens the filters, unless they are open already. */
async function openFilters(user: User) {
  if (filterTrigger().getAttribute("aria-expanded") !== "true") await user.click(filterTrigger());
}

const filterPanel = () => screen.getByRole("form", { name: "Patient filters" });

/** A date range's field in the open panel: its group, then its end. */
const dateField = (range: "Date of Birth" | "Last Visit", end: "From" | "To") =>
  within(within(filterPanel()).getByRole("group", { name: range })).getByLabelText(
    end,
  ) as HTMLInputElement;

/** Sets a date field of the open panel as a date picker does: a whole `YYYY-MM-DD` or nothing. */
const setDate = (range: "Date of Birth" | "Last Visit", end: "From" | "To", value: string) =>
  fireEvent.change(dateField(range, end), { target: { value } });

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
  it("offers a Filter button, closed, that counts no filter at first", async () => {
    await renderSearched();

    expect({
      badge: filterTrigger().querySelector('[data-slot="badge"]'),
      name: filterTrigger().getAttribute("aria-label"),
      panel: screen.queryByRole("form", { name: "Patient filters" }),
    }).toEqual({ badge: null, name: "Filter", panel: null });
  });

  it("offers the gender, All by default, and a from–to date range for each date", async () => {
    const user = userEvent.setup();
    await renderSearched();

    await openFilters(user);

    const dates = (["Date of Birth", "Last Visit"] as const).flatMap((range) =>
      (["From", "To"] as const).map((end) => dateField(range, end)),
    );
    expect({
      dates: dates.map((input) => [
        input.type,
        input.getAttribute("aria-invalid"),
        input.getAttribute("aria-describedby"),
      ]),
      genders: within(filterPanel())
        .getAllByRole("radio")
        .map((choice) => [choice.textContent, choice.getAttribute("aria-checked")]),
      notes: within(filterPanel()).queryAllByRole("paragraph"),
    }).toEqual({
      dates: Array(4).fill(["date", "false", null]),
      genders: [
        ["All", "true"],
        ["Male", "false"],
        ["Female", "false"],
      ],
      notes: [],
    });
  });

  it("keeps the gender chosen when it is pressed again", async () => {
    const user = userEvent.setup();
    await renderSearched();
    await openFilters(user);

    await user.click(within(filterPanel()).getByRole("radio", { name: "All" }));

    expect(
      within(filterPanel()).getByRole("radio", { name: "All" }).getAttribute("aria-checked"),
    ).toBe("true");
  });

  it("forgets a change never applied once the panel closes", async () => {
    const user = userEvent.setup();
    await renderSearched();
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Male" }));
    setDate("Last Visit", "From", "2026-09-01");

    await user.keyboard("{Escape}");
    await openFilters(user);

    expect({
      count: filterTrigger().getAttribute("aria-label"),
      gender: within(filterPanel())
        .getByRole("radio", { name: "All" })
        .getAttribute("aria-checked"),
      lastVisitFrom: dateField("Last Visit", "From").value,
    }).toEqual({ count: "Filter", gender: "true", lastVisitFrom: "" });
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
      count: filterTrigger().getAttribute("aria-label"),
      open: screen.queryByRole("form", { name: "Patient filters" }),
      sent: lastSearchSent(requests),
    }).toEqual({
      count: "Filter, 1 set",
      open: null,
      sent: "/api/patient-search?q=tan&gender=male&startIndex=1&count=5",
    });
  });

  it.each([
    ["Date of Birth", "birthDate", "1980-01-01", "1999-12-31"],
    ["Last Visit", "lastVisit", "2026-08-01", "2026-10-20"],
  ] as const)(
    "filters by a %s range from the first page, and counts each end",
    async (range, field, from, to) => {
      const user = userEvent.setup();
      const asked = `${field}From=${from}&${field}To=${to}`;
      const { requests } = await renderSearch({
        [TAN_PAGE_1]: FIRST_OF_TWO,
        [TAN_PAGE_2]: SECOND_OF_TWO,
        [tanPage(1, asked)]: page([TAN_HUI_MIN], 1, 1),
      });
      await search("tan");
      await toSecondPage();

      await openFilters(user);
      setDate(range, "From", from);
      setDate(range, "To", to);
      await apply(user);
      await screen.findByText(/^Showing 1 to 1 of 1 patients$/);

      expect({
        badge: within(filterTrigger()).getByText("2").dataset.slot,
        count: filterTrigger().getAttribute("aria-label"),
        sent: lastSearchSent(requests),
      }).toEqual({
        badge: "badge",
        count: "Filter, 2 set",
        sent: `/api/patient-search?q=tan&${asked}&startIndex=1&count=5`,
      });
    },
  );

  it.each([
    ["Date of Birth", "1990-01-02", "1990-01-01"],
    ["Last Visit", "2026-10-31", "2026-09-01"],
  ] as const)(
    "refuses a %s from-date after its to-date in the panel, and reads nothing",
    async (range, from, to) => {
      const user = userEvent.setup();
      const { requests } = await renderSearch({ [TAN_PAGE_1]: FIRST_OF_TWO });
      await search("tan");
      await screen.findByRole("table");
      const before = searchesSent(requests).length;

      await openFilters(user);
      setDate(range, "From", from);
      setDate(range, "To", to);
      await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));

      expect({
        count: filterTrigger().getAttribute("aria-label"),
        describedBy: [dateField(range, "From"), dateField(range, "To")].map((input) =>
          input.getAttribute("aria-describedby"),
        ),
        invalid: [dateField(range, "From"), dateField(range, "To")].map((input) =>
          input.getAttribute("aria-invalid"),
        ),
        sent: searchesSent(requests).length - before,
      }).toEqual({
        count: "Filter",
        describedBy: Array(2).fill(
          within(filterPanel()).getByText(`The ${range} from-date must not be after its to-date.`)
            .id,
        ),
        invalid: ["true", "true"],
        sent: 0,
      });
    },
  );

  it("clears the refusal once the range is put right", async () => {
    const user = userEvent.setup();
    await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "birthDateFrom=1990-01-01&birthDateTo=1990-01-02")]: FIRST_OF_TWO,
    });
    await search("tan");
    await screen.findByRole("table");
    await openFilters(user);
    setDate("Date of Birth", "From", "1990-01-02");
    setDate("Date of Birth", "To", "1990-01-01");
    await user.click(within(filterPanel()).getByRole("button", { name: "Apply" }));

    setDate("Date of Birth", "From", "1990-01-01");
    setDate("Date of Birth", "To", "1990-01-02");
    await apply(user);
    await openFilters(user);

    expect({
      invalid: dateField("Date of Birth", "From").getAttribute("aria-invalid"),
      message: screen.queryByText(/from-date must not be after/),
    }).toEqual({ invalid: "false", message: null });
  });

  it("clears every filter on Reset, reading the search unfiltered from its first page", async () => {
    const user = userEvent.setup();
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "gender=female&lastVisitTo=2026-10-31")]: FIRST_OF_TWO,
      [tanPage(6, "gender=female&lastVisitTo=2026-10-31")]: SECOND_OF_TWO,
    });
    await search("tan");
    await screen.findByRole("table");
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Female" }));
    setDate("Last Visit", "To", "2026-10-31");
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
      lastVisitTo: dateField("Last Visit", "To").value,
      sent,
    }).toEqual({
      count: "Filter",
      gender: "true",
      lastVisitTo: "",
      sent: "/api/patient-search?q=tan&startIndex=1&count=5",
    });
  });

  it("keeps a filter and a sort together", async () => {
    const user = userEvent.setup();
    const { requests } = await renderSearch({
      [TAN_PAGE_1]: FIRST_OF_TWO,
      [tanPage(1, "gender=male")]: FIRST_OF_TWO,
      [tanPage(1, "gender=male&sortBy=birthDate&sortOrder=ascending")]: FIRST_OF_TWO,
    });
    await search("tan");
    await screen.findByRole("table");
    await openFilters(user);
    await user.click(within(filterPanel()).getByRole("radio", { name: "Male" }));
    await apply(user);
    await screen.findByRole("table");

    await userEvent.click(sortButton("Date of Birth"));
    await waitFor(() =>
      expect(columnHeader("Date of Birth")).toHaveAttribute("aria-sort", "ascending"),
    );

    expect(lastSearchSent(requests)).toBe(
      "/api/patient-search?q=tan&gender=male&sortBy=birthDate&sortOrder=ascending&startIndex=1&count=5",
    );
  });
});
