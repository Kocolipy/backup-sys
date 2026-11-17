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
  it("lays out Tan Wei Ming's two service records open, the empty third folded, then his dates", async () => {
    await renderDetails(json(TAN_WEI_MING));

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect([
      within(overview)
        .getAllByRole("button")
        .map((button) => `${button.textContent} ${button.getAttribute("aria-expanded")}`),
      pairsIn(overview),
    ]).toEqual([
      ["Service record 1 true", "Service record 2 true", "Service record 3 false"],
      [
        "Service Type: NSman",
        "Rank: CPT",
        "Unit / Service: 3 SIR / Army",
        "Vocation: Infantry Officer",
        "Service Type: DXO",
        "Rank: ME2",
        "Unit / Service: RSS Supreme / Navy",
        "Vocation: Naval Safety Officer",
        "Enlistment Date: 15 Jan 2018",
        "ORD Date: 14 Jan 2020",
        "MR Date: —",
        "ROD Date: 08 Sep 2037",
      ],
    ]);
  });

  it("shows an empty service record's every field as unknown once opened", async () => {
    const user = userEvent.setup();
    await renderDetails(json(TAN_WEI_MING));
    const overview = await page().findByRole("region", { name: "Military Overview" });

    await user.click(within(overview).getByRole("button", { name: "Service record 3" }));

    expect(pairsIn(overview).slice(8, 12)).toEqual([
      "Service Type: —",
      "Rank: —",
      "Unit / Service: —",
      "Vocation: —",
    ]);
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

  it("shows each detail a patient has none of as unknown, with every slot folded and no Next of kin", async () => {
    await renderDetails(json(TAN_WEI_MING_BARE));

    await page().findByRole("region", { name: "Contact Details" });
    expect([
      pairsIn(region("Contact Details")),
      within(region("Next of Kin / Emergency")).queryByText("No next of kin recorded") !== null,
      pairsIn(region("Military Overview")),
      within(region("Military Overview"))
        .getAllByRole("button")
        .map((button) => button.getAttribute("aria-expanded")),
    ]).toEqual([
      ["Phone: —", "Email address: —", "Residential address: —"],
      true,
      ["Enlistment Date: —", "ORD Date: —", "MR Date: —", "ROD Date: —"],
      ["false", "false", "false"],
    ]);
  });

  it("names a unit with no service, or a service with no unit, alone", async () => {
    await renderDetails(
      json(
        withMilitaryHistory([
          { extension: [{ url: "unit", valueString: "3 SIR" }], url: "serviceRecord" },
          { extension: [{ url: "service", valueString: "Navy" }], url: "serviceRecord" },
        ]),
      ),
    );

    const overview = await page().findByRole("region", { name: "Military Overview" });
    expect(pairsIn(overview).filter((pair) => pair.startsWith("Unit / Service"))).toEqual([
      "Unit / Service: 3 SIR",
      "Unit / Service: Navy",
    ]);
  });

  it("lays out a slot for every service record past the third", async () => {
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
      "Service Type: NSF",
      "Rank: Error",
      "Unit / Service: Army",
      "Vocation: Signaller",
      "Enlistment Date: Error",
      "ORD Date: 01 Mar 2001",
      "MR Date: —",
      "ROD Date: Error",
    ]);
  });

  it("shows a malformed unit as Error beside its service, and a record holding only a malformed field open", async () => {
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
            valueString: "extension[0].extension[1].extension('vocation').valueString",
          },
        ],
      }),
    );
    const overview = await page().findByRole("region", { name: "Military Overview" });

    expect([
      pairsIn(overview).filter((pair) => /^(Unit|Vocation)/.test(pair)),
      within(overview)
        .getAllByRole("button")
        .map((button) => button.getAttribute("aria-expanded")),
    ]).toEqual([
      ["Unit / Service: Error / Army", "Vocation: —", "Unit / Service: —", "Vocation: Error"],
      ["true", "true", "false"],
    ]);
  });
});
