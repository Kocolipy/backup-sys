package arch;

import static org.assertj.core.api.Assertions.assertThat;

import arch.PlantedClasses.Call;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link ArchitectureTest#time_is_read_through_the_clock_bean}, shown reporting each way to read
 * the system clock and passing a read through an injected {@code Clock}. The service's own
 * classes comply, so the rule alone would never be seen to fail.
 *
 * <p>The rule reads calls and exempts configuration by package, so the calls are nested here,
 * outside every {@code config} package, and the exemption is shown on a class planted as bytecode
 * in one by {@link PlantedClasses#plantCalling}.
 */
class ClockRulesTests {

    @TempDir
    Path planted;

    private static final Call INSTANT_NOW =
            Call.toStatic("java.time.Instant", "now", "()Ljava/time/Instant;");

    @Test
    void a_no_argument_now_is_reported() {
        assertThat(violations(new ClassFileImporter().importClasses(ReadsInstantNow.class,
                        ReadsTodayNow.class)))
                .contains("ReadsInstantNow.at")
                .contains("ReadsTodayNow.today");
    }

    @Test
    void a_system_clock_is_reported() {
        assertThat(violations(new ClassFileImporter().importClasses(BuildsTheSystemClock.class)))
                .contains("BuildsTheSystemClock.clock");
    }

    @Test
    void the_systems_millisecond_count_is_reported() {
        assertThat(violations(new ClassFileImporter().importClasses(ReadsTheMillis.class)))
                .contains("ReadsTheMillis.millis");
    }

    @Test
    void a_read_through_an_injected_clock_is_not_reported() {
        assertThat(violations(new ClassFileImporter().importClasses(ReadsTheInjectedClock.class)))
                .isEmpty();
    }

    @Test
    void a_no_argument_now_in_configuration_is_not_reported() {
        assertThat(violations(new ClassFileImporter().importPath(PlantedClasses.plantCalling(
                        planted, "com.example.backend.planted.config.PlantedConfig",
                        INSTANT_NOW))))
                .isEmpty();
    }

    @Test
    void a_no_argument_now_in_a_use_case_is_reported() {
        assertThat(violations(new ClassFileImporter().importPath(PlantedClasses.plantCalling(
                        planted, "com.example.backend.planted.application.PlantedService",
                        INSTANT_NOW))))
                .contains("PlantedService");
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String violations(JavaClasses classes) {
        EvaluationResult result = ArchitectureTest.time_is_read_through_the_clock_bean
                .evaluate(classes);
        return result.hasViolation() ? String.join("\n", result.getFailureReport().getDetails())
                : "";
    }

    // ---- planted classes ----------------------------------------------------------------------

    static class ReadsInstantNow {
        Instant at() {
            return Instant.now();
        }
    }

    static class ReadsTodayNow {
        LocalDate today() {
            return LocalDate.now();
        }
    }

    static class BuildsTheSystemClock {
        Clock clock() {
            return Clock.systemUTC();
        }
    }

    static class ReadsTheMillis {
        long millis() {
            return System.currentTimeMillis();
        }
    }

    static class ReadsTheInjectedClock {
        Instant at(Clock clock) {
            return Instant.now(clock);
        }

        LocalDate today(Clock clock) {
            return LocalDate.now(clock);
        }

        Instant instant(Clock clock) {
            return clock.instant();
        }
    }
}
