package com.awesomesoft.features.domain;

public enum AuditOperation {
    FLAG_CREATED,
    FLAG_UPDATED,
    FLAG_ARCHIVED,
    FLAG_LOCKED,
    FLAG_UNLOCKED,
    FLAG_DELETED,
    OVERRIDE_SET,
    OVERRIDE_REMOVED,
    TOKEN_CREATED,
    TOKEN_REVOKED
}
