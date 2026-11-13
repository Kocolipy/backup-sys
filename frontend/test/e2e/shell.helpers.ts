import type { Page } from "@playwright/test";

/**
 * The navigation sidebar, which the signed-in layout renders beside every page.
 * It has no accessible name of its own, so it is found by the `Sidebar`
 * primitive's slot: the one place an e2e spec depends on that markup.
 */
export const sidebar = (page: Page) => page.locator('[data-slot="sidebar"]');
