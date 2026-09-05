package com.awesomesoft.features.config;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.util.UUID;

/** Principal of a backend-token call — the consuming application resolved from the bearer token. */
public record ApplicationPrincipal(UUID applicationId, String slug) implements AuthenticatedPrincipal {

    /**
     * What lands in logs and in the {@code actor_username} column of the change journal, so a flag
     * created by an application's own startup registration is distinguishable from a panel edit.
     */
    @Override
    public String getName() {
        return "token:" + slug;
    }
}
