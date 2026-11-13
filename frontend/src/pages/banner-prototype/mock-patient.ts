/** Mock data for the patient banner prototype; nothing here is read from the backend. */

export interface ServiceRecord {
  id: string;
  label: string;
  rank: string;
  service: "ARMY" | "NAVY" | "AIR FORCE";
  serviceType: string;
  /** When this record was last synced from its source. */
  syncedAt: string;
  unit: string;
  vocation: string;
}

export interface MockPatient {
  aCode: string;
  age: number;
  allergies: readonly string[];
  /** When the alerts strip (allergies, deficiencies, statuses) was last synced. */
  alertsSyncedAt: string;
  bloodGroup: string;
  deficiencies: readonly string[];
  /** When the demographics (gender, age, DOB, blood group) were last synced. */
  demographicsSyncedAt: string;
  dentalStatus: string;
  dob: string;
  gender: string;
  mcsDispositionCode: string;
  name: string;
  nric: string;
  pes: string;
  /** When the medical readiness fields (MCS, PES, dental, A code) were last synced. */
  readinessSyncedAt: string;
  serviceRecords: readonly ServiceRecord[];
  statuses: readonly string[];
}

export const MOCK_PATIENT: MockPatient = {
  aCode: "A1",
  age: 26,
  allergies: [
    "Penicillin",
    "Sulfonamides",
    "NSAID",
    "Aspirin",
    "Codeine",
    "Morphine",
    "Latex",
    "Iodine contrast",
    "Peanuts",
    "Shellfish",
    "Eggs",
    "Bee stings",
    "Cephalosporins",
    "Tetracycline",
    "Erythromycin",
    "Chlorhexidine",
  ],
  alertsSyncedAt: "2026-11-13T09:30:00+08:00",
  bloodGroup: "O+",
  deficiencies: ["G6PD"],
  demographicsSyncedAt: "2026-11-13T10:42:00+08:00",
  dentalStatus: "D1 (Expired)",
  dob: "08-Sep-1997",
  gender: "Male",
  mcsDispositionCode: "FIT (Temp)",
  name: "Tan Wei Ming",
  nric: "S9734567D",
  pes: "-",
  readinessSyncedAt: "2026-11-10T14:02:00+08:00",
  serviceRecords: [
    {
      id: "sr-1",
      label: "Service Record 1",
      rank: "CPT",
      service: "ARMY",
      serviceType: "NSman",
      syncedAt: "2026-11-12T16:20:00+08:00",
      unit: "3 SIR",
      vocation: "Infantry Officer",
    },
    {
      id: "sr-2",
      label: "Service Record 2",
      rank: "LTA",
      service: "NAVY",
      serviceType: "Regular",
      syncedAt: "2026-11-08T08:15:00+08:00",
      unit: "RSS Endurance",
      vocation: "Naval Warfare Officer",
    },
  ],
  statuses: ["P", "R2", "R3"],
};
