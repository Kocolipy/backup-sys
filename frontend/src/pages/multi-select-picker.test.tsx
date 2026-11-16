import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import { MultiSelectPicker } from "./multi-select-picker";

type Fruit = "APPLE" | "APRICOT" | "BANANA" | "CHERRY";

const FRUITS: readonly Fruit[] = ["APPLE", "APRICOT", "BANANA", "CHERRY"];

const FRUIT_LABELS: Readonly<Record<Fruit, string>> = {
  APPLE: "Apple",
  APRICOT: "Apricot",
  BANANA: "Banana",
  CHERRY: "Cherry",
};

const FRUIT_COPY = {
  list: "Fruits",
  name: "Fruit",
  noMatch: "No fruit matches.",
  search: "Search fruits",
};

/** A Fruit picker holding `selected`, every change it reports recorded and not applied. */
function renderPicker(selected: readonly Fruit[] = []) {
  const onChange = vi.fn<(next: readonly Fruit[]) => void>();
  render(
    <MultiSelectPicker
      copy={FRUIT_COPY}
      labels={FRUIT_LABELS}
      onChange={onChange}
      options={FRUITS}
      selected={selected}
    />,
  );
  return { onChange };
}

/** A Fruit picker that starts with nothing selected and takes every change it reports. */
function renderHeldPicker() {
  function Held() {
    const [selected, setSelected] = useState<readonly Fruit[]>([]);
    return (
      <MultiSelectPicker
        copy={FRUIT_COPY}
        labels={FRUIT_LABELS}
        onChange={setSelected}
        options={FRUITS}
        selected={selected}
      />
    );
  }
  render(<Held />);
}

type User = ReturnType<typeof userEvent.setup>;

const trigger = () => screen.getByRole("button", { name: /^Fruit: / });
const openList = (user: User) => user.click(trigger());
const shownOptions = () => screen.getAllByRole("option").map((option) => option.textContent);
const clearings = () => screen.queryAllByRole("option", { name: "Clear selection" });

describe("MultiSelectPicker", () => {
  it.each([
    ["Fruit: Any", []],
    ["Fruit: 1 selected", ["BANANA"]],
    ["Fruit: 2 selected", ["APPLE", "CHERRY"]],
  ] as const)('names its trigger "%s" for %j selected', (name, selected) => {
    renderPicker(selected);

    expect(trigger()).toHaveAccessibleName(name);
  });

  it("lists every option by its label alone, in the set's order", async () => {
    const user = userEvent.setup();
    renderPicker();

    await openList(user);

    expect(shownOptions()).toEqual(["Apple", "Apricot", "Banana", "Cherry"]);
  });

  it("names its search and its list for a screen reader, the list taking many", async () => {
    const user = userEvent.setup();
    renderPicker();
    const popup = trigger().getAttribute("aria-haspopup");

    await openList(user);

    expect({
      popup,
      search: screen.getByRole("combobox", { name: "Fruits" }).tagName,
      placeholder: screen.getByRole("combobox").getAttribute("placeholder"),
      multiselectable: screen.getByRole("listbox").getAttribute("aria-multiselectable"),
    }).toEqual({
      popup: "dialog",
      search: "INPUT",
      placeholder: "Search fruits",
      multiselectable: "true",
    });
  });

  it("narrows the list to the labels the search matches", async () => {
    const user = userEvent.setup();
    renderPicker();

    await openList(user);
    await user.type(screen.getByRole("combobox"), "ap");

    expect(shownOptions().sort()).toEqual(["Apple", "Apricot"]);
  });

  it("says so when the search matches no option", async () => {
    const user = userEvent.setup();
    renderPicker();

    await openList(user);
    await user.type(screen.getByRole("combobox"), "zzz");

    expect(screen.getByText(/^No fruit matches\.$/)).toBeInTheDocument();
  });

  it.each([
    ["adds an unselected option it is pressed on", ["APPLE"], "Banana", ["APPLE", "BANANA"]],
    [
      "removes a selected option it is pressed on, keeping the rest",
      ["APPLE", "BANANA"],
      "Apple",
      ["BANANA"],
    ],
  ] as const)("%s", async (_name, selected, pressed, reported) => {
    const user = userEvent.setup();
    const { onChange } = renderPicker(selected);

    await openList(user);
    await user.click(screen.getByRole("option", { name: pressed }));

    expect(onChange.mock.calls).toEqual([[reported]]);
  });

  it("marks each selected option checked, row and checkbox alike", async () => {
    const user = userEvent.setup();
    renderPicker(["APRICOT"]);

    await openList(user);

    expect(
      ["Apricot", "Banana"].map((name) => {
        const option = screen.getByRole("option", { name });
        return [
          option.getAttribute("aria-checked"),
          option.querySelector('[data-slot="checkbox"]')?.getAttribute("data-state"),
        ];
      }),
    ).toEqual([
      ["true", "checked"],
      ["false", "unchecked"],
    ]);
  });

  // The row is the control: its checkbox only shows the state, so it is no stop of its own.
  it("keeps each checkbox out of the Tab order and from a screen reader", async () => {
    const user = userEvent.setup();
    renderPicker();

    await openList(user);

    const checkboxes = screen
      .getAllByRole("option")
      .map((option) => option.querySelector('[data-slot="checkbox"]'));
    expect(
      new Set(
        checkboxes.map(
          (box) =>
            `tabindex ${box?.getAttribute("tabindex")}, aria-hidden ${box?.getAttribute("aria-hidden")}`,
        ),
      ),
    ).toEqual(new Set(["tabindex -1, aria-hidden true"]));
  });

  it("stays open across picks, so several are picked in one visit", async () => {
    const user = userEvent.setup();
    renderHeldPicker();

    await openList(user);
    await user.click(screen.getByRole("option", { name: "Cherry" }));
    await user.click(screen.getByRole("option", { name: "Apple" }));

    expect(trigger()).toHaveAccessibleName("Fruit: 2 selected");
  });

  it("offers no clearing with nothing selected", async () => {
    const user = userEvent.setup();
    renderPicker();

    await openList(user);

    expect(clearings()).toEqual([]);
  });

  it("offers clearing first and last in the list, each set apart, while something is selected", async () => {
    const user = userEvent.setup();
    renderPicker(["BANANA"]);

    await openList(user);

    expect({
      shown: shownOptions(),
      separators: within(screen.getByRole("listbox")).getAllByRole("separator").length,
    }).toEqual({
      shown: ["Clear selection", "Apple", "Apricot", "Banana", "Cherry", "Clear selection"],
      separators: 2,
    });
  });

  it("offers both clearings still while a search hides every option", async () => {
    const user = userEvent.setup();
    renderPicker(["BANANA"]);

    await openList(user);
    await user.type(screen.getByRole("combobox"), "zzz");

    expect(shownOptions()).toEqual(["Clear selection", "Clear selection"]);
  });

  it.each([
    ["first", 0],
    ["last", 1],
  ])("clears every selection in one step from the %s clearing", async (_where, index) => {
    const user = userEvent.setup();
    const { onChange } = renderPicker(["APPLE", "CHERRY"]);

    await openList(user);
    await user.click(clearings()[index]!);

    expect(onChange.mock.calls).toEqual([[[]]]);
  });

  it("works from the keyboard alone: open, search, pick, close", async () => {
    const user = userEvent.setup();
    renderHeldPicker();

    trigger().focus();
    await user.keyboard("{Enter}");
    await vi.waitFor(() => expect(screen.getByRole("combobox")).toHaveFocus());
    await user.keyboard("cher{Enter}");
    const picked = screen.getByRole("option", { name: "Cherry" }).getAttribute("aria-checked");
    await user.keyboard("{Escape}");

    await vi.waitFor(() => expect(trigger()).toHaveFocus());
    expect([picked, screen.queryByRole("combobox"), trigger().textContent]).toEqual([
      "true",
      null,
      "Fruit: 1 selected",
    ]);
  });

  it("moves between options with the arrow keys", async () => {
    const user = userEvent.setup();
    renderHeldPicker();

    await openList(user);
    await user.keyboard("{ArrowDown}{Enter}");

    expect(
      screen
        .getAllByRole("option")
        .map((option) => [option.textContent, option.getAttribute("aria-checked")]),
    ).toEqual([
      ["Clear selection", null],
      ["Apple", "false"],
      ["Apricot", "true"],
      ["Banana", "false"],
      ["Cherry", "false"],
      ["Clear selection", null],
    ]);
  });
});
