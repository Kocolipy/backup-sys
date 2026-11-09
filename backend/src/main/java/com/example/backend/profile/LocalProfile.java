package com.example.backend.profile;

/**
 * The one developer profile: a run on a developer's machine, and never a deployment's.
 *
 * <p>{@code local} migrates in-process, which the deployed service never does, so no deployment
 * runs it, and one thing is granted under it alone, reading the name from here: the
 * development fixtures ({@code APP_DEV_FIXTURES_ENABLED}), which {@code
 * scim.config.ScimSeedConfig} refuses at startup outside it — {@code authorization.yaml}
 * activates their Group Permissions under the same name, so the two are renamed together.
 *
 * <p>A name only: this class knows no bean and no business module.
 */
public final class LocalProfile {

    /** The profile's name, as {@code SPRING_PROFILES_ACTIVE} spells it. */
    public static final String NAME = "local";

    private LocalProfile() {
    }
}
