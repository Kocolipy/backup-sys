package arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.auth.domain.EpicTokenSet;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rule in {@link ArchitectureTest} that keeps Epic's tokens out of the browser (ADR 0012, D29),
 * shown reporting a planted controller — the service's own controllers comply, so the rule alone
 * would never be seen to fail.
 */
class EpicTokenRulesTests {

    @Test
    void aControllerReachingTheKeptTokensIsReported() {
        assertThat(violations(ArchitectureTest.epic_tokens_never_reach_a_rest_controller,
                RendersTheKeptTokens.class))
                .contains("RendersTheKeptTokens");
    }

    private static String violations(ArchRule rule, Class<?>... planted) {
        EvaluationResult result = rule.evaluate(new ClassFileImporter().importClasses(planted));
        return result.hasViolation() ? String.join("\n", result.getFailureReport().getDetails())
                : "";
    }

    // ---- planted classes ----------------------------------------------------------------------

    @RestController
    static class RendersTheKeptTokens {
        public Optional<EpicTokenSet> tokens() {
            return Optional.empty();
        }
    }
}
