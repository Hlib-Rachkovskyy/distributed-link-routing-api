package com.project.urlshortener.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @ParameterizedTest
    @ValueSource(ints = {4, 6, 8, 10, 50})
    void testGenerateRandomShortCode_Length(int length) {
        String code = encoder.generateRandomShortCode(length);
        assertEquals(length, code.length());
    }

    @Test
    void testGenerateRandomShortCode_Characters() {
        String code = encoder.generateRandomShortCode(100);
        assertTrue(code.matches("^[a-zA-Z0-9]+$"));
    }
}
