import { cn } from "@/lib/utils";

import { AlertChips, Allergies, Field, ServiceChip } from "./banner-parts";
import { ServiceZone, ZONE } from "./banner-layout-b";
import type { MockPatient, ServiceRecord } from "./mock-patient";

/** Four equal columns, so each row spans the box and the two rows line up. */
const FIELD_ROW = "grid grid-cols-2 gap-x-8 gap-y-3 sm:grid-cols-4";

/**
 * The service fields with each label above its value, as in the left box: two
 * rows of three columns, the pager sitting at the end of the first.
 */
function StackedServiceFields({ record }: { record: ServiceRecord }) {
  return (
    <dl className="grid grid-cols-3 gap-x-8 gap-y-6">
      <Field label="Type">{record.serviceType}</Field>
      <Field label="Rank">{record.rank}</Field>
      <Field label="Service">
        <ServiceChip service={record.service} />
      </Field>
      <Field label="Unit">{record.unit}</Field>
      <Field className="col-span-2" label="Vocation">
        {record.vocation}
      </Field>
    </dl>
  );
}

/**
 * Layout C: Layout B with its first two zones merged into one — demographics
 * in a row, medical readiness in a row beneath it — beside the service
 * record, under the same full-width alerts strip, here along the top.
 */
export function BannerLayoutC({ patient }: { patient: MockPatient }) {
  return (
    <section
      aria-label="Patient"
      className="w-full overflow-hidden rounded-lg bg-linear-to-r from-primary to-ring text-primary-foreground shadow-md"
    >
      <div className="flex flex-wrap items-center gap-x-8 gap-y-2 border-b border-primary-foreground/20 bg-primary/40 px-6 py-3">
        <Allergies allergies={patient.allergies} />
        <AlertChips items={patient.deficiencies} label="Deficiency" />
        <AlertChips items={patient.statuses} label="Status" />
      </div>

      <div className="grid grid-cols-1 divide-primary-foreground/20 md:grid-cols-[2fr_1.75fr] md:divide-x">
        <div className={cn(ZONE, "gap-6")}>
          <dl className={FIELD_ROW}>
            <Field label="Gender">{patient.gender}</Field>
            <Field label="Age">{patient.age}</Field>
            <Field label="DOB">{patient.dob}</Field>
            <Field label="Blood group">{patient.bloodGroup}</Field>
          </dl>
          <dl className={FIELD_ROW}>
            <Field label="MCS">{patient.mcsDispositionCode}</Field>
            <Field label="PES">{patient.pes}</Field>
            <Field label="Dental">{patient.dentalStatus}</Field>
            <Field label="A code">{patient.aCode}</Field>
          </dl>
        </div>

        <ServiceZone
          fields={(record) => <StackedServiceFields record={record} />}
          records={patient.serviceRecords}
        />
      </div>
    </section>
  );
}
