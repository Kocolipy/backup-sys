package com.example.backend.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.backend.audit.CapturedLog;
import com.example.backend.observability.InvalidSourceFields.Field;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * How the fields of a patient-source answer whose format was wrong are reported (ADR 0020; ADR
 * 0003): one {@code WARN} for the whole answer, naming the read and each field's path, and a count
 * for each field, by read and field; an answer with none reports nothing. Which fields a read
 * finds invalid, and their paths, is each read's adapter's.
 */
class InvalidSourceFieldsTests {

    private final MeterRegistry meters = new SimpleMeterRegistry();

    private final InvalidSourceFields report = new InvalidSourceFields(meters);

    private static final List<Field> THREE = List.of(new Field("entry[0].outcome", "outcome"),
            new Field("entry[2].issueDate", "issueDate"),
            new Field("entry[4].outcome", "outcome"));

    private static List<ILoggingEvent> warnings(CapturedLog captured) {
        return captured.withAction(Level.WARN, LogEvent.LOCAL_ACTION,
                "patient_source.invalid_fields");
    }

    @Test
    void anAnswerWithInvalidFieldsIsOneWarnNamingTheReadAndEachPathInOrder() {
        try (CapturedLog captured = CapturedLog.attach()) {
            report.report(SourceRead.NUMC, THREE);

            assertThat(warnings(captured)).singleElement().satisfies(warning -> {
                assertThat(List.of(warning.getLevel(), warning.getFormattedMessage()))
                        .containsExactly(Level.WARN, "Patient source answered fields in the wrong"
                                + " format, each shown as Error");
                assertThat(CapturedLog.fields(warning))
                        .containsEntry("event.outcome", "success")
                        .containsEntry("event.category", List.of("network"))
                        .containsEntry("event.type", List.of("info"))
                        .containsEntry("app.patient_source.read", "numc")
                        .containsEntry("app.patient_source.invalid_fields", List.of(
                                "entry[0].outcome", "entry[2].issueDate", "entry[4].outcome"));
            });
        }
    }

    @Test
    void anAnswerWithNoInvalidFieldReportsNothing() {
        try (CapturedLog captured = CapturedLog.attach()) {
            report.report(SourceRead.NUMC, List.of());

            assertThat(List.of(warnings(captured), meters.getMeters()))
                    .containsExactly(List.of(), List.of());
        }
    }

    @Test
    void eachInvalidFieldCountsOnceByItsReadAndItsFieldOverEveryAnswer() {
        report.report(SourceRead.NUMC, THREE);
        report.report(SourceRead.NUMC, List.of(new Field("entry[1].type", "type")));

        Map<String, Double> counted = new TreeMap<>();
        for (Counter counter : meters.find("patient_source.invalid_fields").counters()) {
            counted.put(counter.getId().getTag("read") + " " + counter.getId().getTag("field"),
                    counter.count());
        }
        assertThat(counted).isEqualTo(Map.of(
                "numc outcome", 2.0,
                "numc issueDate", 1.0,
                "numc type", 1.0));
    }

    @Test
    void theCounterSaysWhatItCounts() {
        report.report(SourceRead.NUMC, THREE);

        assertThat(meters.find("patient_source.invalid_fields").counter().getId()
                .getDescription())
                .isEqualTo("Fields of the patient source's answers whose format was wrong, by"
                        + " read and field");
    }
}
