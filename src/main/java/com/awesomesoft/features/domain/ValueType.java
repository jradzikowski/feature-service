package com.awesomesoft.features.domain;

import tools.jackson.databind.JsonNode;

/** Declared type of a flag's value; every value is stored as JSON text and validated against it. */
public enum ValueType {
    BOOLEAN,
    STRING,
    NUMBER,
    JSON;

    /** True when the parsed JSON value conforms to this declared type. */
    public boolean matches(JsonNode value) {
        return switch (this) {
            case BOOLEAN -> value.isBoolean();
            case STRING -> value.isTextual();
            case NUMBER -> value.isNumber();
            case JSON -> !value.isMissingNode();
        };
    }
}
