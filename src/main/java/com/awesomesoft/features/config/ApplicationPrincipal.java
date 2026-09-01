package com.awesomesoft.features.config;

import java.util.UUID;

/** Principal of an Evaluation API call — the consuming application resolved from the bearer token. */
public record ApplicationPrincipal(UUID applicationId, String slug) {
}
