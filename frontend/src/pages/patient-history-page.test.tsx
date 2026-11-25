import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, holding, json } from "@/auth/session-adapter.testHelpers";
import {
  page as main,
  TAN_WEI_MING,
  TAN_WEI_MING_READ,
  withPatientInContext,
} from "@/pages/patient.testHelpers";
import { renderSignedIn } from "@/shell/signed-in-shell.testHelpers";

/** A Medical Officer: a session holding `patient:read`, which each history page requires. */
const CLINICIAN = holding(["patient:read"]);

/**
 * Each page laid out by `PatientHistoryPage`: its route, its title, its
 * history's read of Tan Wei Ming's first page — written out, so the path is
 * checked against the contract and not against the code under test — and the
 * field its page's rows travel in.
 */
const PAGES = [
  ["FFI", "/ffi", "FFI History", "patient-ffi", "ffis"],
  ["NUMC", "/numc", "NUMC", "patient-numc", "records"],
  ["ARMC", "/armc", "ARMC", "patient-armc", "records"],
  ["HSP", "/hsp", "HSP History", "patient-hsp", "records"],
] as const;

const firstPage = (api: string) => `GET /api/${api}/eq081-VQEgP8drUUqCWzHfw3?startIndex=1&count=5`;

/** The page's headings, each beside whether it is kept for screen readers only. */
const headingsShown = () =>
  main()
    .getAllByRole("heading")
    .map((heading) => [heading.textContent, heading.classList.contains("sr-only")]);

/** The text of the card holding "No patient in context", or null with none. */
const noPatientCard = () =>
  main().queryByText("No patient in context")?.closest("[data-slot=card]")?.textContent ?? null;

/** The page's reads of its history among what it sent. */
const historyReads = (api: string, sent: readonly { method: string; path: string }[]) =>
  sent.map(({ method, path }) => `${method} ${path}`).filter((read) => read.includes(`/${api}/`));

afterEach(() => {
  clearEhrContext();
});

describe.each(PAGES)("the %s page", (_name, route, title, api, rows) => {
  // The shell drops its title while a patient is in context, so the history
  // card's heading is the page's one h1 and the title is announced once.
  it("heads the page with the Patient card, then a card headed by its title beside its Filter button", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    await renderSignedIn(route, CLINICIAN, {
      [firstPage(api)]: json({ [rows]: [], startIndex: 1, totalResults: 0 }),
      [TAN_WEI_MING_READ]: json(TAN_WEI_MING),
    });
    await screen.findByText("No records found.");

    const patientCard = main().getByRole("region", { name: "Patient" });
    const heading = main().getByRole("heading", { level: 1, name: title });
    const historyCard = heading.closest("[data-slot=card]")!;
    expect({
      filterBesideHeading: heading.parentElement!.querySelector("button")?.textContent,
      headings: headingsShown(),
      patientCardFirst: Boolean(
        patientCard.compareDocumentPosition(historyCard) & Node.DOCUMENT_POSITION_FOLLOWING,
      ),
    }).toEqual({
      filterBesideHeading: "Filter",
      headings: [
        ["Tan Wei Ming", false],
        [title, false],
      ],
      patientCardFirst: true,
    });
  });

  it("is headed by its title, shown, above the Patient card's no patient in context, with no history card and no read, with none", async () => {
    const { requests } = await renderSignedIn(route, CLINICIAN, {});

    expect({
      filter: screen.queryByRole("button", { name: /^Filter/ }),
      headings: headingsShown(),
      noPatient: noPatientCard(),
      reads: historyReads(api, requests()),
    }).toEqual({
      filter: null,
      headings: [[title, false]],
      noPatient: "No patient in context",
      reads: [],
    });
  });

  it("is headed by its title, shown, above the Patient card's no patient in context, with no history card and no read, for a 404", async () => {
    withPatientInContext(TAN_WEI_MING.id);
    const { requests } = await renderSignedIn(route, CLINICIAN, {
      [TAN_WEI_MING_READ]: bare(404),
    });
    await main().findByText("No patient in context");

    expect({
      filter: screen.queryByRole("button", { name: /^Filter/ }),
      headings: headingsShown(),
      noPatient: noPatientCard(),
      reads: historyReads(api, requests()),
    }).toEqual({
      filter: null,
      headings: [[title, false]],
      noPatient: "No patient in context",
      reads: [],
    });
  });
});
