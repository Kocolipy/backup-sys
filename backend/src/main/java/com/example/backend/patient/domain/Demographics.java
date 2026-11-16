package com.example.backend.patient.domain;

/**
 * A Patient's Demographic details, as a read by id copies them from the in-house patient source
 * (/docs/domain/patient.md, "Demographic details"): how they are reached, their next of kin and
 * their military service. Each is optional: {@code null} when the source holds none.
 *
 * <p>Restricted, as the rest of the Patient is: {@link #toString()} prints none of it.
 *
 * @param phone              the first phone the source lists
 * @param email              the first email the source lists
 * @param residentialAddress the text of the first {@code home} address
 * @param nextOfKin          the first contact coded as the Patient's next of kin
 * @param militaryHistory    the Patient's military service
 */
public record Demographics(String phone, String email, String residentialAddress,
        NextOfKin nextOfKin, MilitaryHistory militaryHistory) {

    /** A Patient the source holds no Demographic details for. */
    public static final Demographics NONE = new Demographics(null, null, null, null, null);

    @Override
    public String toString() {
        return "Demographics[redacted]";
    }
}
