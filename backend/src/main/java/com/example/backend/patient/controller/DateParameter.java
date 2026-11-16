package com.example.backend.patient.controller;

/**
 * An end of a range over a history's date column as the caller wrote it, before
 * {@link QueryParameters#range} reads it at the column's precision — where a blank end is no end
 * at all.
 *
 * <p>The wrapper is a binding choice. A query parameter binds to it through its one constructor,
 * and of a parameter given more than once only the first value reaches that constructor, as for
 * any parameter bound to one value of a type of its own. Bound to {@code String}, the same
 * parameter would arrive as every value joined by commas, which no precision reads, so a date
 * given twice would be refused rather than read as its first
 * ({@code PatientArmcControllerTests::aDateGivenTwiceIsReadAsItsFirst}).
 *
 * @param text the end as written
 */
public record DateParameter(String text) {
}
