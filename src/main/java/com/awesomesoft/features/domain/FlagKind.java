package com.awesomesoft.features.domain;

/** Lifecycle categories after Fowler/Hodgson; RELEASE and EXPERIMENT feed the stale-flag report. */
public enum FlagKind {
    RELEASE,
    EXPERIMENT,
    OPS,
    PERMISSION
}
