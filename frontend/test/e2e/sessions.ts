/**
 * The sessions `auth.setup.ts` saves and the projects in `playwright.config.ts`
 * replay, under the ignored `test/e2e/.auth/` directory. Kept free of
 * `@playwright/test` so the config can import it.
 */

/** The `guest` development fixture's session, which the `user` project replays. */
export const USER_SESSION = "test/e2e/.auth/user.json";

/** The seeded Admin's session, which the `admin` project replays. */
export const ADMIN_SESSION = "test/e2e/.auth/admin.json";
