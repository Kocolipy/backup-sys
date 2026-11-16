import { expect, test, type Page } from "@playwright/test";

import {
  deprovisionGroup,
  deprovisionUser,
  E2E_PREFIX,
  provisionGroup,
  provisionUser,
  rowOf,
  runId,
  searchCard,
  workerConnector,
} from "./scim.helpers";

/**
 * The Users and Groups cards of the Accounts page read a page at a time and
 * searched by name, against the running stack. The spec seeds one more
 * User and one more Group than a page holds, all named under a tag of its own,
 * so a search for the tag is a listing of exactly them that spans two pages
 * whatever else the directory holds; the Admin then moves between the pages.
 *
 * In the `admin` project. Everything it seeds is its own, provisioned over SCIM
 * with an `e2e-` name and removed in a `finally`.
 */

/** What one page of either card holds: `LISTING_PAGE_SIZE` in `accounts-api.ts`. */
const PAGE_SIZE = 20;
const SEEDED = PAGE_SIZE + 1;

const RUN = runId();
const TAG = `${E2E_PREFIX}paged-${RUN}`;
const connector = workerConnector("accounts-paging");

/** The `index`th seeded name, zero-padded so the names sort as they number. */
const nameOf = (index: number) => `${TAG}-${String(index).padStart(2, "0")}`;

const pager = (page: Page, card: "Users" | "Groups") =>
  page.getByRole("navigation", { name: `${card} pages` });

/** Moves one card to its next page and back, checking where each page sits and what it shows. */
async function pageThrough(page: Page, card: "Users" | "Groups") {
  const table = page.getByRole("table", { name: card });
  await expect(pager(page, card)).toContainText(
    `Showing 1 to ${PAGE_SIZE} of ${SEEDED} ${card.toLowerCase()}`,
  );
  await expect(rowOf(page, table, nameOf(1))).toBeVisible();
  await expect(rowOf(page, table, nameOf(SEEDED))).toHaveCount(0);
  await expect(pager(page, card).getByRole("button", { name: "Previous" })).toBeDisabled();

  await pager(page, card).getByRole("button", { name: "Next" }).click();

  await expect(pager(page, card)).toContainText(
    `Showing ${SEEDED} to ${SEEDED} of ${SEEDED} ${card.toLowerCase()}`,
  );
  await expect(rowOf(page, table, nameOf(SEEDED))).toBeVisible();
  await expect(rowOf(page, table, nameOf(1))).toHaveCount(0);
  await expect(pager(page, card).getByRole("button", { name: "Next" })).toBeDisabled();

  await pager(page, card).getByRole("button", { name: "Previous" }).click();

  await expect(pager(page, card)).toContainText(
    `Showing 1 to ${PAGE_SIZE} of ${SEEDED} ${card.toLowerCase()}`,
  );
}

test("an admin pages through and searches both cards", async ({ page }) => {
  // Twenty-one provisions, each a password hash.
  test.setTimeout(180_000);
  const userIds: string[] = [];
  const groupIds: string[] = [];
  try {
    for (let index = 1; index <= SEEDED; index += 1) {
      userIds.push(await provisionUser(connector.scim, nameOf(index)));
      groupIds.push(await provisionGroup(connector.scim, nameOf(index)));
    }
    await page.goto("/accounts");
    await searchCard(page, "Users", TAG);
    await pageThrough(page, "Users");
    await searchCard(page, "Groups", TAG);
    await pageThrough(page, "Groups");

    // A search that matches nothing says so, rather than that nothing is provisioned.
    await searchCard(page, "Groups", `${TAG}-none`);
    await expect(page.getByText(`No groups match "${TAG}-none".`)).toBeVisible();
  } finally {
    for (const id of groupIds) await deprovisionGroup(connector.scim, id);
    for (const id of userIds) await deprovisionUser(connector.scim, id);
  }
});
