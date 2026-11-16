import type { ComponentProps, ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { byCode } from "@/lib/source-field";

/** A badge's look, as the Badge takes it. */
export type BadgeVariant = NonNullable<ComponentProps<typeof Badge>["variant"]>;

/**
 * How a closed set's code from the patient source is shown, handed to `shown`:
 * by its label in `labels`, or as it came when outside the set (ADR 0020).
 */
export function labelled<C extends string>(
  labels: Readonly<Record<C, string>>,
): (code: string) => string {
  return (code) => byCode(labels, code, code);
}

/**
 * How a closed set's code from the patient source is shown as a badge, handed
 * to `shown`: its label in `labels` on its look in `badges`, or the code as it
 * came on a neutral badge when outside the set (ADR 0020).
 */
export function badged<C extends string>(
  labels: Readonly<Record<C, string>>,
  badges: Readonly<Record<C, BadgeVariant>>,
): (code: string) => ReactNode {
  return (code) => (
    <Badge variant={byCode(badges, code, "outline")}>{byCode(labels, code, code)}</Badge>
  );
}
