package com.example.backend.patient.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A Patient's military service: their service records, in the source's order, and its dates,
 * each shown as received (ADR 0020) — present, missing or in the wrong format, a date that is
 * none to the day among the last. Restricted: neither this nor a {@link ServiceRecord} prints
 * any of it.
 *
 * @param serviceRecords the service records, in order; never {@code null}
 * @param enlistmentDate the day they enlisted
 * @param ordDate        their Operationally Ready Date
 * @param mrDate         their MR date
 * @param rodDate        their Run Out Date
 */
public record MilitaryHistory(List<ServiceRecord> serviceRecords,
        SourceField<LocalDate> enlistmentDate, SourceField<LocalDate> ordDate,
        SourceField<LocalDate> mrDate, SourceField<LocalDate> rodDate) {

    public MilitaryHistory {
        serviceRecords = serviceRecords == null ? List.of() : List.copyOf(serviceRecords);
        Objects.requireNonNull(enlistmentDate, "enlistmentDate");
        Objects.requireNonNull(ordDate, "ordDate");
        Objects.requireNonNull(mrDate, "mrDate");
        Objects.requireNonNull(rodDate, "rodDate");
    }

    /** The date {@code date} names. */
    public SourceField<LocalDate> date(ServiceDate date) {
        return date.of.apply(this);
    }

    @Override
    public String toString() {
        return "MilitaryHistory[redacted]";
    }

    /**
     * A date of the military service, by the url of the sub-extension it is carried under, in the
     * source's answer and in the served {@code Patient} alike, in the order both list them.
     */
    public enum ServiceDate {
        ENLISTMENT("enlistmentDate", MilitaryHistory::enlistmentDate),
        ORD("ordDate", MilitaryHistory::ordDate),
        MR("mrDate", MilitaryHistory::mrDate),
        ROD("rodDate", MilitaryHistory::rodDate);

        private final String url;
        private final Function<MilitaryHistory, SourceField<LocalDate>> of;

        ServiceDate(String url, Function<MilitaryHistory, SourceField<LocalDate>> of) {
            this.url = url;
            this.of = of;
        }

        /** The url of the sub-extension the date is carried under. */
        public String url() {
            return url;
        }
    }

    /**
     * One service record, each field shown as received.
     *
     * @param serviceType the kind of service ({@code NSman})
     * @param rank        the rank held
     * @param unit        the unit served in
     * @param service     the service the unit belongs to ({@code Army})
     * @param vocation    the vocation served as
     */
    public record ServiceRecord(SourceField<String> serviceType, SourceField<String> rank,
            SourceField<String> unit, SourceField<String> service,
            SourceField<String> vocation) {

        public ServiceRecord {
            Objects.requireNonNull(serviceType, "serviceType");
            Objects.requireNonNull(rank, "rank");
            Objects.requireNonNull(unit, "unit");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(vocation, "vocation");
        }

        /** The field {@code field} names. */
        public SourceField<String> field(Field field) {
            return field.of.apply(this);
        }

        @Override
        public String toString() {
            return "ServiceRecord[redacted]";
        }

        /**
         * A field of a service record, by the url of the sub-extension it is carried under, in
         * the source's answer and in the served {@code Patient} alike, in the order both list
         * them.
         */
        public enum Field {
            SERVICE_TYPE("serviceType", ServiceRecord::serviceType),
            RANK("rank", ServiceRecord::rank),
            UNIT("unit", ServiceRecord::unit),
            SERVICE("service", ServiceRecord::service),
            VOCATION("vocation", ServiceRecord::vocation);

            private final String url;
            private final Function<ServiceRecord, SourceField<String>> of;

            Field(String url, Function<ServiceRecord, SourceField<String>> of) {
                this.url = url;
                this.of = of;
            }

            /** The url of the sub-extension the field is carried under. */
            public String url() {
                return url;
            }
        }
    }
}
