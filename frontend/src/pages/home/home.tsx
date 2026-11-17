import { Card, CardContent } from "@/components/ui/card";

/**
 * Home, the signed-in page at `/`: the landing page of a session without
 * `patient:read` (`landingPath`), and refused to one holding it, which its
 * guard sends on to Patient Search. Headed by its title, it says on a card of
 * its own that the account has no access to patients, and reads nothing. The
 * other signed-in pages are linked from the navigation sidebar
 * (`navigation-sidebar.tsx`), not from here.
 */
export function Home() {
  return (
    <Card>
      <CardContent className="flex flex-col gap-4">
        <p className="text-sm text-muted-foreground">Your account has no access to patients.</p>
      </CardContent>
    </Card>
  );
}
