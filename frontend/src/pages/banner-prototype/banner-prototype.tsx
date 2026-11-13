import { useState } from "react";

import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";

import { BannerLayoutA } from "./banner-layout-a";
import { BannerLayoutB } from "./banner-layout-b";
import { BannerLayoutC } from "./banner-layout-c";
import { MOCK_PATIENT, type MockPatient } from "./mock-patient";

/** Each candidate banner layout; a new layout is one entry here. */
const LAYOUTS: readonly {
  id: string;
  label: string;
  render: (p: MockPatient) => React.ReactNode;
}[] = [
  { id: "a", label: "Layout A", render: (p) => <BannerLayoutA patient={p} /> },
  { id: "b", label: "Layout B", render: (p) => <BannerLayoutB patient={p} /> },
  { id: "c", label: "Layout C", render: (p) => <BannerLayoutC patient={p} /> },
];

/** A prototype page comparing patient banner layouts over mock data. */
export function BannerPrototype() {
  const [layoutId, setLayoutId] = useState(LAYOUTS[0].id);
  const layout = LAYOUTS.find((l) => l.id === layoutId) ?? LAYOUTS[0];
  return (
    <div className="flex w-full flex-col gap-4">
      <ToggleGroup
        aria-label="Banner layout"
        className="self-start"
        onValueChange={(v) => v && setLayoutId(v)}
        type="single"
        value={layoutId}
        variant="outline"
      >
        {LAYOUTS.map((l) => (
          <ToggleGroupItem key={l.id} value={l.id}>
            {l.label}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>
      {layout.render(MOCK_PATIENT)}
    </div>
  );
}
