package arch;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.auth.controller.AuthController;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The rules in {@link ArchitectureTest} that hold what a web adapter may declare and reach — a
 * Permission on every protected handler (ADR 0015), no credential's stored form, and no
 * transaction of its own (ADR 0013's as-13) — each shown reporting a planted class and passing
 * what it allows. The service's own classes comply, so the rules alone would never be seen to
 * fail.
 *
 * <p>The handler rule reads annotations, so its planted classes are nested here: none is in a
 * package Spring scans. The other rules read a class's package, so their planted classes are
 * written as bytecode into a directory of their own by {@link PlantedClasses#plant}: one field per
 * type reached, which ArchUnit reads as a dependency on it.
 */
class WebAdapterRulesTests {

    @TempDir
    Path planted;

    // ---- every protected handler declares a Permission (ADR 0015) ----------------------------

    @Test
    void a_handler_with_no_permission_is_reported() {
        assertThat(violations(ArchitectureTest.every_protected_handler_declares_a_permission,
                new ClassFileImporter().importClasses(UndeclaredHandlerController.class)))
                .contains("UndeclaredHandlerController.read");
    }

    @Test
    void a_handler_declaring_its_permission_and_a_method_that_is_no_handler_are_not_reported() {
        assertThat(violations(ArchitectureTest.every_protected_handler_declares_a_permission,
                new ClassFileImporter().importClasses(DeclaredHandlerController.class)))
                .isEmpty();
    }

    /**
     * The exemptions, on the service's own adapters: the login surface's handlers and SCIM's
     * {@code /Me} declare no Permission, and the rule passes them by name. A compliant handler
     * beside them keeps the rule's subject from being empty, which it refuses.
     */
    @Test
    void the_public_and_token_only_adapters_are_not_reported() throws ClassNotFoundException {
        // ScimMeController is package-private, so it is loaded by name.
        Class<?> scimMe = Class.forName("com.example.backend.scim.controller.ScimMeController");

        assertThat(violations(ArchitectureTest.every_protected_handler_declares_a_permission,
                new ClassFileImporter().importClasses(AuthController.class, scimMe,
                        DeclaredHandlerController.class)))
                .isEmpty();
    }

    // ---- no credential's stored form reaches a web adapter -----------------------------------

    @Test
    void a_web_adapter_reaching_the_scim_user_is_reported() {
        assertThat(violations(ArchitectureTest.a_scim_user_credential_never_reaches_a_web_adapter,
                        "com.example.backend.scim.controller.Planted",
                        "com.example.backend.scim.domain.ScimUser"))
                .contains("Planted");
    }

    @Test
    void a_web_adapter_reaching_a_class_nested_in_the_scim_user_is_reported() {
        assertThat(violations(ArchitectureTest.a_scim_user_credential_never_reaches_a_web_adapter,
                        "com.example.backend.auth.controller.Planted",
                        "com.example.backend.scim.domain.ScimUser$Planted"))
                .contains("Planted");
    }

    /** The name is matched whole: the projection and the profile only begin with it. */
    @Test
    void a_web_adapter_reaching_the_user_projection_or_profile_is_not_reported() {
        assertThat(violations(ArchitectureTest.a_scim_user_credential_never_reaches_a_web_adapter,
                        "com.example.backend.scim.controller.Planted",
                        "com.example.backend.scim.application.ScimUserResource",
                        "com.example.backend.scim.domain.ScimUserProfile"))
                .isEmpty();
    }

    @Test
    void a_web_adapter_reaching_a_token_digest_is_reported() {
        assertThat(violations(
                        ArchitectureTest.a_connector_token_digest_never_reaches_a_web_adapter,
                        "com.example.backend.scim.controller.Planted",
                        "com.example.backend.scim.domain.ConnectorTokenDigest"))
                .contains("Planted");
    }

    @Test
    void a_web_adapter_reaching_the_token_aggregate_is_reported() {
        assertThat(violations(
                        ArchitectureTest.a_connector_token_digest_never_reaches_a_web_adapter,
                        "com.example.backend.scim.controller.Planted",
                        "com.example.backend.scim.domain.ScimConnectorToken"))
                .contains("Planted");
    }

    /** The name is matched whole: the token repository only begins with the aggregate's. */
    @Test
    void a_web_adapter_reaching_the_token_summary_or_repository_is_not_reported() {
        assertThat(violations(
                        ArchitectureTest.a_connector_token_digest_never_reaches_a_web_adapter,
                        "com.example.backend.scim.controller.Planted",
                        "com.example.backend.scim.application.ConnectorTokenSummary",
                        "com.example.backend.scim.domain.ScimConnectorTokenRepository"))
                .isEmpty();
    }

    @Test
    void a_class_outside_the_web_adapters_reaching_the_scim_user_is_not_reported() {
        assertThat(violations(ArchitectureTest.a_scim_user_credential_never_reaches_a_web_adapter,
                        "com.example.backend.scim.application.Planted",
                        "com.example.backend.scim.domain.ScimUser"))
                .isEmpty();
    }

    // ---- a web adapter opens no transaction (ADR 0013's as-13) --------------------------------

    @Test
    void a_web_adapter_reaching_a_transaction_template_is_reported() {
        assertThat(violations(ArchitectureTest.web_adapters_open_no_transaction,
                        "com.example.backend.auth.controller.Planted",
                        "org.springframework.transaction.support.TransactionTemplate"))
                .contains("Planted");
    }

    @Test
    void a_web_adapter_reaching_a_transaction_manager_is_reported() {
        assertThat(violations(ArchitectureTest.web_adapters_open_no_transaction,
                        "com.example.backend.scim.controller.Planted",
                        "org.springframework.transaction.PlatformTransactionManager"))
                .contains("Planted");
    }

    @Test
    void a_web_adapter_reaching_a_propagation_and_a_use_case_reaching_a_template_are_not_reported() {
        PlantedClasses.plant(planted, "com.example.backend.auth.controller.Planted",
                "org.springframework.transaction.annotation.Propagation");
        PlantedClasses.plant(planted, "com.example.backend.auth.application.Planted",
                "org.springframework.transaction.support.TransactionTemplate");

        assertThat(violations(ArchitectureTest.web_adapters_open_no_transaction,
                new ClassFileImporter().importPath(planted)))
                .isEmpty();
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String violations(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);
        return result.hasViolation() ? String.join("\n", result.getFailureReport().getDetails())
                : "";
    }

    /** {@code rule}'s violations on one planted class, {@code name}, holding a field per type. */
    private String violations(ArchRule rule, String name, String... reaches) {
        return violations(rule,
                new ClassFileImporter().importPath(PlantedClasses.plant(planted, name, reaches)));
    }

    // ---- planted classes ----------------------------------------------------------------------

    @RestController
    static class UndeclaredHandlerController {
        @GetMapping("/api/planted")
        String read() {
            return "";
        }
    }

    @RestController
    static class DeclaredHandlerController {
        @GetMapping("/api/planted")
        @PreAuthorize("hasAuthority('user:read')")
        String read() {
            return describe();
        }

        String describe() {
            return "";
        }
    }
}
