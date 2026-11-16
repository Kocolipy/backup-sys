package com.example.backend.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.backend.audit.domain.AuditOperation;
import com.example.backend.authorization.domain.Permission;
import com.example.backend.patient.domain.ArmcBoardType;
import com.example.backend.patient.domain.ArmcOutcome;
import com.example.backend.patient.domain.DispenseStatus;
import com.example.backend.patient.domain.FfiHigherAuthority;
import com.example.backend.patient.domain.FfiOutcome;
import com.example.backend.patient.domain.FfiStatus;
import com.example.backend.patient.domain.FfiType;
import com.example.backend.patient.domain.MedicalCentre;
import com.example.backend.patient.domain.NumcOutcome;
import com.example.backend.patient.domain.NumcType;
import com.example.backend.patient.domain.SourceCode;
import com.example.backend.scim.domain.LockCause;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * Every closed set the API exposes is the same set in code and in {@code docs/openapi.yaml}, in
 * both directions.
 *
 * <p>The fixture suites see an enum value only when a response carries it, so a member added to a
 * Java enum that no fixture emits would pass them while the document — and the SPA, which mirrors
 * the document — stays without it. This sweep compares each Java vocabulary with every enum the
 * document gives it, member by member, whatever any response says. The SPA's copy is then held to
 * the same enums by the root's SPA–API contract check ({@code /scripts/spa-contract/README.md}).
 *
 * <p>The Permissions split as the domain splits them (ADR 0015): a session holds every Permission
 * that is not token-only — the delegatable ones — a Group carries only the delegatable ones, and a connector token only the token-grantable ones, whose
 * write halves no session holds. No Permission escapes the comparison: one no session holds is
 * {@linkplain Permission#tokenOnly() token-only}, so token-grantable by that definition, and the
 * token enums compare it.
 *
 * <p>An FFI's four closed sets of its own — its FFI Type, Outcome, Higher Authority and Status —
 * a NUMC Record's two — its NUMC Type and NUMC Outcome — an ARMC Board's two — its ARMC Board
 * Type and ARMC Outcome — a Medication Record's Dispense Status, and Medical Centre, the set an
 * FFI's Submitting MC and a Medication Record's Dispensary share, are carried by their codes,
 * not their names, and are compared so.
 *
 * <p>An enum the document refers to by {@code $ref} — the audit trail's {@code operation} query
 * parameter, the Group Permissions' request and listing — is the schema compared here, so it is
 * not listed again.
 */
class VocabularyContractTests {

    private static final OpenApiContract CONTRACT = OpenApiContract.load();

    private static final String SESSION_PERMISSIONS =
            "#/components/schemas/UserResponse/properties/permissions/items";

    private static final String DELEGATABLE_PERMISSIONS =
            "#/components/schemas/DelegatablePermission";

    private static final String TOKEN_PERMISSIONS_REQUESTED =
            "#/components/schemas/ConnectorTokenPermissions/items";

    private static final String TOKEN_PERMISSIONS_LISTED =
            "#/components/schemas/ConnectorTokenSummary/properties/permissions/items";

    private static final String AUDIT_OPERATIONS = "#/components/schemas/AuditOperation";

    private static final String LOCK_CAUSES =
            "#/components/schemas/AccountSummary/properties/lockCause";

    private static final String FFI_TYPES = "#/components/schemas/FfiType";

    private static final String FFI_OUTCOMES = "#/components/schemas/FfiOutcome";

    private static final String FFI_HIGHER_AUTHORITIES = "#/components/schemas/FfiHigherAuthority";

    private static final String FFI_STATUSES = "#/components/schemas/FfiStatus";

    private static final String NUMC_TYPES = "#/components/schemas/NumcType";

    private static final String NUMC_OUTCOMES = "#/components/schemas/NumcOutcome";

    private static final String ARMC_BOARD_TYPES = "#/components/schemas/ArmcBoardType";

    private static final String ARMC_OUTCOMES = "#/components/schemas/ArmcOutcome";

    private static final String MEDICAL_CENTRES = "#/components/schemas/MedicalCentre";

    private static final String DISPENSE_STATUSES = "#/components/schemas/DispenseStatus";

    private static final Predicate<Permission> HELD_BY_A_SESSION = permission -> !permission.tokenOnly();

    @Test
    void the_permissions_a_session_can_hold_are_the_documented_session_permissions() {
        assertThat(CONTRACT.enumDisagreements(SESSION_PERMISSIONS, permissions(HELD_BY_A_SESSION)))
                .isEmpty();
    }

    @Test
    void the_delegatable_permissions_are_the_documented_delegatable_permissions() {
        assertThat(CONTRACT.enumDisagreements(DELEGATABLE_PERMISSIONS,
                permissions(Permission::delegatable))).isEmpty();
    }

    @Test
    void the_token_grantable_permissions_are_the_permissions_a_token_is_requested_with() {
        assertThat(CONTRACT.enumDisagreements(TOKEN_PERMISSIONS_REQUESTED,
                permissions(Permission::tokenGrantable))).isEmpty();
    }

    @Test
    void the_token_grantable_permissions_are_the_permissions_a_listed_token_carries() {
        assertThat(CONTRACT.enumDisagreements(TOKEN_PERMISSIONS_LISTED,
                permissions(Permission::tokenGrantable))).isEmpty();
    }

    @Test
    void every_audit_operation_is_a_documented_audit_operation_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(AUDIT_OPERATIONS, names(AuditOperation.values())))
                .isEmpty();
    }

    @Test
    void every_lock_cause_is_a_documented_lock_cause_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(LOCK_CAUSES, names(LockCause.values()))).isEmpty();
    }

    @Test
    void every_ffi_type_is_a_documented_ffi_type_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(FFI_TYPES, codes(FfiType.values()))).isEmpty();
    }

    @Test
    void every_ffi_outcome_is_a_documented_ffi_outcome_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(FFI_OUTCOMES, codes(FfiOutcome.values())))
                .isEmpty();
    }

    @Test
    void every_higher_authority_is_a_documented_higher_authority_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(FFI_HIGHER_AUTHORITIES,
                codes(FfiHigherAuthority.values()))).isEmpty();
    }

    @Test
    void every_ffi_status_is_a_documented_ffi_status_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(FFI_STATUSES, codes(FfiStatus.values())))
                .isEmpty();
    }

    @Test
    void every_numc_type_is_a_documented_numc_type_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(NUMC_TYPES, codes(NumcType.values()))).isEmpty();
    }

    @Test
    void every_numc_outcome_is_a_documented_numc_outcome_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(NUMC_OUTCOMES, codes(NumcOutcome.values())))
                .isEmpty();
    }

    @Test
    void every_armc_board_type_is_a_documented_armc_board_type_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(ARMC_BOARD_TYPES, codes(ArmcBoardType.values())))
                .isEmpty();
    }

    @Test
    void every_armc_outcome_is_a_documented_armc_outcome_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(ARMC_OUTCOMES, codes(ArmcOutcome.values())))
                .isEmpty();
    }

    @Test
    void every_medical_centre_is_a_documented_medical_centre_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(MEDICAL_CENTRES, codes(MedicalCentre.values())))
                .isEmpty();
    }

    @Test
    void every_dispense_status_is_a_documented_dispense_status_and_no_other_is() {
        assertThat(CONTRACT.enumDisagreements(DISPENSE_STATUSES, codes(DispenseStatus.values())))
                .isEmpty();
    }

    /** A closed set's members as the wire spells them: by their code. */
    private static List<String> codes(SourceCode[] values) {
        return Arrays.stream(values).map(SourceCode::code).toList();
    }

    /** The Permissions the filter admits, as the wire spells them. */
    private static List<String> permissions(Predicate<Permission> which) {
        return Arrays.stream(Permission.values()).filter(which).map(Permission::value).toList();
    }

    /** A vocabulary's members as the wire spells them: by {@code name()}. */
    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
