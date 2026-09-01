package com.awesomesoft.features;

import com.awesomesoft.features.domain.ValueType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ValueTypeTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void booleanAcceptsOnlyJsonBooleans() {
        assertThat(ValueType.BOOLEAN.matches(mapper.readTree("true"))).isTrue();
        assertThat(ValueType.BOOLEAN.matches(mapper.readTree("false"))).isTrue();
        assertThat(ValueType.BOOLEAN.matches(mapper.readTree("\"true\""))).isFalse();
        assertThat(ValueType.BOOLEAN.matches(mapper.readTree("1"))).isFalse();
    }

    @Test
    void stringAcceptsOnlyJsonStrings() {
        assertThat(ValueType.STRING.matches(mapper.readTree("\"variant-a\""))).isTrue();
        assertThat(ValueType.STRING.matches(mapper.readTree("42"))).isFalse();
        assertThat(ValueType.STRING.matches(mapper.readTree("{}"))).isFalse();
    }

    @Test
    void numberAcceptsIntegersAndDecimals() {
        assertThat(ValueType.NUMBER.matches(mapper.readTree("42"))).isTrue();
        assertThat(ValueType.NUMBER.matches(mapper.readTree("3.14"))).isTrue();
        assertThat(ValueType.NUMBER.matches(mapper.readTree("\"42\""))).isFalse();
    }

    @Test
    void jsonAcceptsAnyStructure() {
        assertThat(ValueType.JSON.matches(mapper.readTree("{\"limit\":5}"))).isTrue();
        assertThat(ValueType.JSON.matches(mapper.readTree("[1,2]"))).isTrue();
        assertThat(ValueType.JSON.matches(mapper.readTree("true"))).isTrue();
    }
}
