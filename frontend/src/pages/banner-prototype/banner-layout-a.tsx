import { useState } from "react";

import {
  AlertChips,
  Allergies,
  BANNER_LABEL,
  Field,
  ServiceChip,
  ServiceRecordSelect,
} from "./banner-parts";
import type { MockPatient } from "./mock-patient";

/**
 * Layout A: alerts (allergies, deficiencies, statuses) along the top right,
 * a row of clinical fields, then a row of service fields driven by the
 * service record picked in the bottom-right select.
 */
export function BannerLayoutA({ patient }: { patient: MockPatient }) {
  const [recordId, setRecordId] = useState(patient.serviceRecords[0]?.id);
  const index = patient.serviceRecords.findIndex((r) => r.id === recordId);
  const record = patient.serviceRecords[index];

  return (
    <section
      aria-label="Patient"
      className="flex w-full flex-col gap-5 rounded-lg bg-linear-to-r from-primary to-ring px-8 py-5 text-primary-foreground shadow-md"
    >
      <div className="flex flex-wrap items-center justify-end gap-x-6 gap-y-2">
        <Allergies allergies={patient.allergies} />
        <AlertChips items={patient.deficiencies} label="Deficiency" />
        <AlertChips items={patient.statuses} label="Status" />
      </div>

      <dl className="flex flex-wrap justify-between gap-x-8 gap-y-4">
        <Field label="Gender">{patient.gender}</Field>
        <Field label="Age">{patient.age}</Field>
        <Field label="DOB">{patient.dob}</Field>
        <Field label="Blood group">{patient.bloodGroup}</Field>
        <Field label="MCS disposition code">{patient.mcsDispositionCode}</Field>
        <Field label="PES">{patient.pes}</Field>
        <Field label="Dental status">{patient.dentalStatus}</Field>
        <Field label="A code">{patient.aCode}</Field>
      </dl>

      <div className="flex flex-wrap items-end justify-between gap-6">
        {record && (
          <dl className="flex flex-wrap gap-x-14 gap-y-4">
            <Field label="Service">
              <ServiceChip service={record.service} />
            </Field>
            <Field label="Service type">{record.serviceType}</Field>
            <Field label="Rank">{record.rank}</Field>
            <Field label="Unit">{record.unit}</Field>
            <Field label="Vocation">{record.vocation}</Field>
          </dl>
        )}
        {patient.serviceRecords.length > 1 && (
          <div className="flex w-64 flex-col gap-1">
            <span className={BANNER_LABEL} id="layout-a-record-label">
              Record {index + 1} of {patient.serviceRecords.length}
            </span>
            <ServiceRecordSelect
              labelledBy="layout-a-record-label"
              onChange={setRecordId}
              records={patient.serviceRecords}
              value={recordId}
            />
          </div>
        )}
      </div>
    </section>
  );
}
