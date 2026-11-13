import { ChevronLeft, ChevronRight } from "lucide-react";
import { type ReactNode, useState } from "react";

import { AlertChips, Allergies, InlineField, ServiceChip, SyncedAt } from "./banner-parts";
import type { MockPatient, ServiceRecord } from "./mock-patient";

export const ZONE = "flex min-w-0 flex-col gap-3 px-6 py-5";
/**
 * A zone's sync caption, in its bottom-right corner on the line of the zone's
 * last row of fields rather than beneath them; the zones are one height, so
 * the three captions also share one line.
 */
const ZONE_CAPTION = "absolute right-6 bottom-5";
const PAGER_BUTTON =
  "flex size-6 items-center justify-center rounded-xs text-primary-foreground hover:bg-primary-foreground/15 focus-visible:ring-2 focus-visible:ring-primary-foreground focus-visible:outline-none disabled:pointer-events-none disabled:opacity-30";

/** Layout B's service fields: label beside value, Unit and Vocation beside Type and Rank. */
function InlineServiceFields({ record }: { record: ServiceRecord }) {
  return (
    <dl className="grid grid-cols-[auto_1fr] gap-x-13 gap-y-2">
      <InlineField className="col-span-2" label="Service">
        <ServiceChip service={record.service} />
      </InlineField>
      <InlineField label="Type">{record.serviceType}</InlineField>
      <InlineField label="Unit">{record.unit}</InlineField>
      <InlineField label="Rank">{record.rank}</InlineField>
      <InlineField label="Vocation">{record.vocation}</InlineField>
    </dl>
  );
}

/**
 * The service zone: the record's fields, drawn by `fields` (Layout B's by
 * default), with a "< 1/2 >" pager in its top right and, with `showSyncedAt`,
 * the shown record's sync time at its foot.
 */
export function ServiceZone({
  fields = (record) => <InlineServiceFields record={record} />,
  records,
  showSyncedAt = false,
}: {
  fields?: (record: ServiceRecord) => ReactNode;
  records: readonly ServiceRecord[];
  showSyncedAt?: boolean;
}) {
  const [index, setIndex] = useState(0);
  const record = records[index];
  return (
    <div className={`relative ${ZONE}`}>
      {records.length > 1 && (
        <div
          aria-label="Service records"
          className="absolute top-5 right-6 flex items-center gap-1"
          role="group"
        >
          <button
            aria-label="Previous service record"
            className={PAGER_BUTTON}
            disabled={index === 0}
            onClick={() => setIndex((i) => i - 1)}
            type="button"
          >
            <ChevronLeft aria-hidden className="size-4" />
          </button>
          <span
            aria-live="polite"
            className="min-w-8 text-center text-sm font-semibold tabular-nums"
          >
            {index + 1}/{records.length}
          </span>
          <button
            aria-label="Next service record"
            className={PAGER_BUTTON}
            disabled={index === records.length - 1}
            onClick={() => setIndex((i) => i + 1)}
            type="button"
          >
            <ChevronRight aria-hidden className="size-4" />
          </button>
        </div>
      )}
      {record && fields(record)}
      {record && showSyncedAt && <SyncedAt className={ZONE_CAPTION} syncedAt={record.syncedAt} />}
    </div>
  );
}

/**
 * Layout B: three zones side by side — demographics, medical readiness, and
 * the service record (paged from its top right) — over a full-width alerts
 * strip. Each zone, and the strip, shows when its own data was last synced.
 */
export function BannerLayoutB({ patient }: { patient: MockPatient }) {
  return (
    <section
      aria-label="Patient"
      className="w-full overflow-hidden rounded-lg bg-linear-to-r from-primary to-ring text-primary-foreground shadow-md"
    >
      <div className="grid grid-cols-1 divide-primary-foreground/20 md:grid-cols-[1fr_1fr_1.75fr] md:divide-x">
        <div className={`relative ${ZONE}`}>
          <dl className="flex flex-col gap-2">
            <InlineField label="Gender">{patient.gender}</InlineField>
            <InlineField label="Age">{patient.age}</InlineField>
            <InlineField label="DOB">{patient.dob}</InlineField>
            <InlineField label="Blood">{patient.bloodGroup}</InlineField>
          </dl>
          <SyncedAt className={ZONE_CAPTION} syncedAt={patient.demographicsSyncedAt} />
        </div>

        <div className={`relative ${ZONE}`}>
          <dl className="flex flex-col gap-2">
            <InlineField label="MCS">{patient.mcsDispositionCode}</InlineField>
            <InlineField label="PES">{patient.pes}</InlineField>
            <InlineField label="Dental">{patient.dentalStatus}</InlineField>
            <InlineField label="A code">{patient.aCode}</InlineField>
          </dl>
          <SyncedAt className={ZONE_CAPTION} syncedAt={patient.readinessSyncedAt} />
        </div>

        <ServiceZone records={patient.serviceRecords} showSyncedAt />
      </div>

      <div className="flex flex-wrap items-center gap-x-8 gap-y-2 border-t border-primary-foreground/20 bg-primary/40 px-6 py-3">
        <Allergies allergies={patient.allergies} />
        <AlertChips items={patient.deficiencies} label="Deficiency" />
        <AlertChips items={patient.statuses} label="Status" />
        <SyncedAt className="ml-auto" syncedAt={patient.alertsSyncedAt} />
      </div>
    </section>
  );
}
