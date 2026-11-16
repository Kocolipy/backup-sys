package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * A Patient's military service: their service records, in the source's order, and its dates,
 * each to the day and each {@code null} when the source holds none. Restricted: neither this nor
 * a {@link ServiceRecord} prints any of it.
 *
 * @param serviceRecords the service records, in order; never {@code null}
 * @param enlistmentDate the day they enlisted
 * @param ordDate        their Operationally Ready Date
 * @param mrDate         their MR date
 * @param rodDate        their Run Out Date
 */
public record MilitaryHistory(List<ServiceRecord> serviceRecords, LocalDate enlistmentDate,
        LocalDate ordDate, LocalDate mrDate, LocalDate rodDate) {

    public MilitaryHistory {
        serviceRecords = serviceRecords == null ? List.of() : List.copyOf(serviceRecords);
    }

    @Override
    public String toString() {
        return "MilitaryHistory[redacted]";
    }

    /**
     * One service record; any field is {@code null} when the source holds none.
     *
     * @param serviceType the kind of service ({@code NSman})
     * @param rank        the rank held
     * @param unit        the unit served in
     * @param service     the service the unit belongs to ({@code Army})
     * @param vocation    the vocation served as
     */
    public record ServiceRecord(String serviceType, String rank, String unit, String service,
            String vocation) {

        @Override
        public String toString() {
            return "ServiceRecord[redacted]";
        }
    }
}
