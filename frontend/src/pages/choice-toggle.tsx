import { useId } from "react";

import { Label } from "@/components/ui/label";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

/**
 * A filter panel's one-of-several choice — All, or one value — as a row of
 * toggles named by `label`, exactly one of them pressed. Pressing the one
 * already pressed keeps it, so the choice is never left empty.
 */
export function ChoiceToggle<V extends string>({
  choices,
  label,
  onChange,
  value,
}: {
  choices: readonly { label: string; value: V }[];
  label: string;
  onChange: (next: V) => void;
  value: V;
}) {
  const labelId = useId();
  return (
    <div className="flex flex-col gap-1">
      <Label id={labelId}>{label}</Label>
      <ToggleGroup
        aria-labelledby={labelId}
        onValueChange={(next) => {
          // Pressing the position already chosen reports "": no position, and no change.
          for (const choice of choices) if (choice.value === next) onChange(choice.value);
        }}
        size="sm"
        type="single"
        value={value}
        variant="outline"
      >
        {choices.map((choice) => (
          <ToggleGroupItem key={choice.value} value={choice.value}>
            {choice.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
    </div>
  );
}
