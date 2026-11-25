import { within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it } from "vitest";

import { clearEhrContext } from "@/auth/ehr-context";
import { bare, holding, json, type WireAnswer } from "@/auth/session-adapter.testHelpers";
import { renderInLayout } from "@/shell/signed-in-shell.testHelpers";

import { DemographicDetails } from "./demographic-details";
import { MILITARY_HISTORY_EXTENSION } from "@/pages/patient-api";
import {
  page,
  TAN_WEI_MING,
  TAN_WEI_MING_BARE,
  TAN_WEI_MING_MALFORMED,
  TAN_WEI_MING_READ,
  withPatientInContext,
} from "@/pages/patient.testHelpers";

/**
 * The details alone, in the signed-in layout that reads the patient in
 * context, with Tan Wei Ming in context and the read of him answered `answer`.
 */
const renderDetails = (answer: WireAnswer) => {
  withPatientInContext(TAN_WEI_MING.id);
  return renderInLayout(
    [{ element: <DemographicDetails />, path: "/" }],
    "/",
    holding(["patient:read"]),
    { [TAN_WEI_MING_READ]: answer },
  );
};

/** The label–value pairs a region shows, each as `label: value`. */
const pairsIn = (region: HTMLElement) =>
  within(region)
    .queryAllByRole("term")
    .map((term) => `${term.textContent}: ${term.nextElementSibling?.textContent}`);

const region = (name: string) => page().getByRole("region", { name });

/** Tan Wei Ming with a Military history of `extension` alone. */
const withMilitaryHistory = (extension: object[]) => ({
  ...TAN_WEI_MING,
  extension: [{ extension, url: MILITARY_HISTORY_EXTENSION }],
});

afterEach(() => clearEhrContext());

describe("DemographicDetails", () => {
  it("lays out Tan Wei Ming's dates before his two open service records, each field in its own row", async () => {
    await renderDetails(json(TAN_WEI_MING));

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect([
      within(overview)
        .getAllByRole("button")
        .map((button) => `${button.textContent} ${button.getAttribute("aria-expanded")}`),
      pairsIn(overview),
    ]).toEqual([
      ["Service record 1 true", "Service record 2 true"],
      [
        "Enlistment Date: 15 Jan 2018",
        "ORD Date: 14 Jan 2020",
        "MR Date: —",
        "ROD Date: 08 Sep 2037",
        "Service Type: NSman",
        "Rank: CPT",
        "Service: Army",
        "Unit: 3 SIR",
        "Vocation: Infantry Officer",
        "Type of Vocation: Combat",
        "Primary MC: Selarang Medical Centre",
        "Dental Centre: Selarang Dental Centre",
        "Service Type: DXO",
        "Rank: ME2",
        "Service: Navy",
        "Unit: RSS Supreme",
        "Vocation: Naval Safety Officer",
        "Type of Vocation: —",
        "Primary MC: —",
        "Dental Centre: —",
      ],
    ]);
  });

  it("shows no empty service record, numbering those it shows in the source's order", async () => {
    await renderDetails(
      json(
        withMilitaryHistory([
          { extension: [{ url: "rank", valueString: "LTA" }], url: "serviceRecord" },
          { url: "serviceRecord" },
          { extension: [{ url: "service", valueString: "Navy" }], url: "serviceRecord" },
        ]),
      ),
    );

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect([
      within(overview)
        .getAllByRole("button")
        .map((button) => button.textContent),
      pairsIn(overview).filter((pair) => /^(Rank|Service):/.test(pair)),
    ]).toEqual([
      ["Service record 1", "Service record 2"],
      ["Rank: LTA", "Service: —", "Rank: —", "Service: Navy"],
    ]);
  });

  it("shows no service record at all for a patient whose every record is empty, only the dates", async () => {
    await renderDetails(json(withMilitaryHistory([{ url: "serviceRecord" }])));

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect([within(overview).queryAllByRole("button"), pairsIn(overview)]).toEqual([
      [],
      ["Enlistment Date: —", "ORD Date: —", "MR Date: —", "ROD Date: —"],
    ]);
  });

  it("shows no badge beside its title", async () => {
    await renderDetails(json(TAN_WEI_MING));

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect(overview.querySelector("[data-slot=badge]")).toBeNull();
  });

  it("folds a service record away, and opens it again", async () => {
    const user = userEvent.setup();
    await renderDetails(json(TAN_WEI_MING));
    const toggle = await page().findByRole("button", { name: "Service record 1" });

    await user.click(toggle);
    const folded = [toggle.getAttribute("aria-expanded"), page().queryByText("Infantry Officer")];
    await user.click(toggle);

    expect([folded, page().getByText("Infantry Officer") !== null]).toEqual([
      ["false", null],
      true,
    ]);
  });

  it("shows his Contact Details", async () => {
    await renderDetails(json(TAN_WEI_MING));

    await page().findByRole("region", { name: "Contact Details" });
    expect(pairsIn(region("Contact Details"))).toEqual([
      "Phone: +65 9123 4567",
      "Email address: tan.hw@email.com",
      "Residential address: Blk 123 Bishan Street 12 #10-456, Singapore 570123",
    ]);
  });

  it("shows his Next of kin", async () => {
    await renderDetails(json(TAN_WEI_MING));

    await page().findByRole("region", { name: "Next of Kin / Emergency" });
    expect(pairsIn(region("Next of Kin / Emergency"))).toEqual([
      "Name: Mdm Lee Siew Kuen",
      "Relationship: Mother",
      "Contact number: +65 8234 5678",
    ]);
  });

  it("shows each detail a patient has none of as unknown, with no service record and no Next of kin", async () => {
    await renderDetails(json(TAN_WEI_MING_BARE));

    await page().findByRole("region", { name: "Contact Details" });
    expect([
      pairsIn(region("Contact Details")),
      within(region("Next of Kin / Emergency")).queryByText("No next of kin recorded") !== null,
      pairsIn(region("Military Overview")),
      within(region("Military Overview")).queryAllByRole("button"),
    ]).toEqual([
      ["Phone: —", "Email address: —", "Residential address: —"],
      true,
      ["Enlistment Date: —", "ORD Date: —", "MR Date: —", "ROD Date: —"],
      [],
    ]);
  });

  it("lays out every service record, past the third too", async () => {
    const record = { extension: [{ url: "rank", valueString: "LTA" }], url: "serviceRecord" };
    await renderDetails(json(withMilitaryHistory([record, record, record, record])));

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect(
      within(overview)
        .getAllByRole("button")
        .map((button) => button.textContent),
    ).toEqual(["Service record 1", "Service record 2", "Service record 3", "Service record 4"]);
  });

  it("renders nothing while there is no patient to show", async () => {
    await renderDetails(bare(404));

    expect(page().queryAllByRole("region")).toEqual([]);
  });

  it("shows a value carrying markup as the text it is", async () => {
    const telecom = [{ system: "phone", value: "<b>+65 9123 4567</b>" }];
    await renderDetails(json({ ...TAN_WEI_MING, telecom }));

    expect(await page().findByText("<b>+65 9123 4567</b>")).toBeInTheDocument();
  });

  it("shows each Contact Detail as received: its value, — when missing, Error when malformed", async () => {
    await renderDetails(json(TAN_WEI_MING_MALFORMED));
    await page().findByRole("region", { name: "Contact Details" });

    expect(pairsIn(region("Contact Details"))).toEqual([
      "Phone: Error",
      "Email address: lim.kl@email.com",
      "Residential address: —",
    ]);
  });

  it("shows each Next of kin detail as received", async () => {
    await renderDetails(json(TAN_WEI_MING_MALFORMED));
    await page().findByRole("region", { name: "Next of Kin / Emergency" });

    expect(pairsIn(region("Next of Kin / Emergency"))).toEqual([
      "Name: —",
      "Relationship: Error",
      "Contact number: +65 8111 2222",
    ]);
  });

  it("shows each service record field and service date as received", async () => {
    await renderDetails(json(TAN_WEI_MING_MALFORMED));
    const overview = await page().findByRole("region", { name: "Military Overview" });

    expect(pairsIn(overview)).toEqual([
      "Enlistment Date: Error",
      "ORD Date: 01 Mar 2001",
      "MR Date: —",
      "ROD Date: Error",
      "Service Type: NSF",
      "Rank: Error",
      "Service: Army",
      "Unit: —",
      "Vocation: Signaller",
      "Type of Vocation: —",
      "Primary MC: —",
      "Dental Centre: —",
    ]);
  });

  it("shows a malformed field as Error, and a record holding only a malformed field open", async () => {
    const extension = [
      { extension: [{ url: "service", valueString: "Army" }], url: "serviceRecord" },
      { url: "serviceRecord" },
    ];
    await renderDetails(
      json({
        ...withMilitaryHistory(extension),
        extension: [
          ...withMilitaryHistory(extension).extension,
          {
            url: "http://example.org/fhir/StructureDefinition/invalid-field",
            valueString: "extension[0].extension[0].extension('unit').valueString",
          },
          {
            url: "http://example.org/fhir/StructureDefinition/invalid-field",
            valueString: "extension[0].extension[1].extension('primaryMc').valueString",
          },
        ],
      }),
    );
    const overview = await page().findByRole("region", { name: "Military Overview" });

    expect([
      pairsIn(overview).filter((pair) => /^(Service|Unit|Primary MC):/.test(pair)),
      within(overview)
        .getAllByRole("button")
        .map((button) => button.getAttribute("aria-expanded")),
    ]).toEqual([
      [
        "Service: Army",
        "Unit: Error",
        "Primary MC: —",
        "Service: —",
        "Unit: —",
        "Primary MC: Error",
      ],
      ["true", "true"],
    ]);
  });
});
