import { ChevronsUpDownIcon } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
  CommandSeparator,
} from "@/components/ui/command";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";

/**
 * One "Clear selection" in {@link MultiSelectPicker}'s list, shown whatever the
 * search says. `value` is its `cmdk` key, distinct per placement.
 */
function ClearSelection({ onClear, value }: { onClear: () => void; value: string }) {
  return (
    <CommandGroup forceMount>
      <CommandItem forceMount onSelect={onClear} value={value}>
        Clear selection
      </CommandItem>
    </CommandGroup>
  );
}

/** What a {@link MultiSelectPicker} calls itself and its parts. */
interface MultiSelectCopy {
  /** The trigger's name for what it picks: "Operation" reads "Operation: Any". */
  name: string;
  /** The list's accessible name: "Operations". */
  list: string;
  /** The search box's placeholder: "Search operations". */
  search: string;
  /** The line shown when the search matches nothing: "No operation matches.". */
  noMatch: string;
}

/**
 * A filter panel's searchable multi-select over a closed set, `options` in
 * their set's order. It lists each by its label — searched and shown as its
 * label, sent as its value — and no count beside any: a listing paged on the
 * server cannot count from the rows on screen. The list stays open across
 * changes, so several can be picked in one visit. `aria-checked` carries the
 * selection, because `cmdk` spends `aria-selected` on the row the keyboard is
 * on.
 *
 * While anything is selected, a "Clear selection" sits both first and last in
 * the list, each set apart by a separator and shown whatever the search says.
 * `cmdk` keys an item by its `value`, so the two carry distinct values behind
 * the one label.
 */
export function MultiSelectPicker<V extends string>({
  copy,
  labels,
  onChange,
  options,
  selected,
}: {
  copy: MultiSelectCopy;
  labels: Readonly<Record<V, string>>;
  onChange: (next: readonly V[]) => void;
  options: readonly V[];
  selected: readonly V[];
}) {
  const toggle = (option: V) =>
    onChange(
      selected.includes(option)
        ? selected.filter((other) => other !== option)
        : [...selected, option],
    );
  const anySelected = selected.length > 0;
  const clear = () => onChange([]);

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button
          className="w-full justify-between border-dashed"
          size="sm"
          type="button"
          variant="outline"
        >
          {copy.name}: {selected.length === 0 ? "Any" : `${selected.length} selected`}
          <ChevronsUpDownIcon aria-hidden="true" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-80 p-0">
        <Command label={copy.list}>
          <CommandInput placeholder={copy.search} />
          <CommandList aria-multiselectable="true">
            <CommandEmpty>{copy.noMatch}</CommandEmpty>
            {anySelected ? (
              <>
                <ClearSelection onClear={clear} value="Clear selection (first)" />
                <CommandSeparator alwaysRender />
              </>
            ) : null}
            <CommandGroup>
              {options.map((option) => {
                const checked = selected.includes(option);
                const label = labels[option];
                return (
                  <CommandItem
                    aria-checked={checked}
                    key={option}
                    onSelect={() => toggle(option)}
                    value={label}
                  >
                    <Checkbox
                      aria-hidden="true"
                      checked={checked}
                      className="pointer-events-none"
                      tabIndex={-1}
                    />
                    {label}
                  </CommandItem>
                );
              })}
            </CommandGroup>
            {anySelected ? (
              <>
                <CommandSeparator alwaysRender />
                <ClearSelection onClear={clear} value="Clear selection (last)" />
              </>
            ) : null}
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
}
