package com.example.backend.patient.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A closed set's member found by its code, exactly: from the patient source, a code no member has
 * is none; from a request's parameter, it is refused, and a blank one is no filter.
 */
class SourceCodeTests {

    /**
     * A closed set carried by code, each code unlike its member's Java name; the SPA would label
     * {@code high-dependency} "High Dependency Unit".
     */
    enum Ward implements SourceCode {
        INTENSIVE_CARE("intensive-care"),
        HIGH_DEPENDENCY("high-dependency");

        private final String code;

        Ward(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    /** A code finds its member exactly: another case, a label or nothing finds none. */
    @Test
    void aCodeFindsItsMemberExactly() {
        assertThat(Arrays.asList(
                SourceCode.of(Ward.class, "high-dependency"),
                SourceCode.of(Ward.class, "HIGH-DEPENDENCY"),
                SourceCode.of(Ward.class, "High Dependency Unit"),
                SourceCode.of(Ward.class, null)))
                .containsExactly(Optional.of(Ward.HIGH_DEPENDENCY), Optional.empty(),
                        Optional.empty(), Optional.empty());
    }

    @ParameterizedTest
    @CsvSource({
        "intensive-care, INTENSIVE_CARE",
        "high-dependency, HIGH_DEPENDENCY"})
    void aParameterIsReadAsTheMemberItsCodeSpells(String code, Ward ward) {
        assertThat(SourceCode.ofParameter(Ward.class, code)).isEqualTo(ward);
    }

    /** A code is matched exactly: a label, another case or a Java name is outside its set. */
    @ParameterizedTest
    @ValueSource(strings = {"High Dependency Unit", "HIGH-DEPENDENCY", "HIGH_DEPENDENCY",
        "maternity"})
    void aParameterOutsideItsClosedSetIsRefused(String code) {
        assertThatThrownBy(() -> SourceCode.ofParameter(Ward.class, code))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A code is outside its closed set");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void noParameterOrABlankOneIsNone(String code) {
        assertThat(SourceCode.ofParameter(Ward.class, code)).isNull();
    }
}
