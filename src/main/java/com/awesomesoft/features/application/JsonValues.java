package com.awesomesoft.features.application;

import com.awesomesoft.features.domain.ValueType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Parses and validates flag values (stored as JSON text) against the flag's declared type. */
@Component
@RequiredArgsConstructor
public class JsonValues {

    private final ObjectMapper objectMapper;

    /** Validates the incoming value against the declared type and returns its canonical JSON text. */
    public String validateAndSerialize(ValueType type, JsonNode value) {
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Value must not be null");
        }
        if (!type.matches(value)) {
            throw new IllegalArgumentException(
                    "Value " + value + " does not match the flag's declared type " + type);
        }
        return value.toString();
    }

    /** Parses stored JSON text; stored values were validated on write, so failures are server errors. */
    public JsonNode parse(String storedValue) {
        if (storedValue == null) {
            return null;
        }
        return objectMapper.readTree(storedValue);
    }
}
