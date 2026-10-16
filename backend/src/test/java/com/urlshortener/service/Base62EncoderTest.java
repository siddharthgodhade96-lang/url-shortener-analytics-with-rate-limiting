package com.urlshortener.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Base62EncoderTest {

    private final Base62Encoder encoder = new Base62Encoder();

    @Test
    void encodesZero() {
        assertEquals("0", encoder.encode(0));
    }

    @Test
    void encodesValuesUsingTheConfiguredBase62Alphabet() {
        assertEquals("z", encoder.encode(35));
        assertEquals("Z", encoder.encode(61));
        assertEquals("10", encoder.encode(62));
        assertEquals("A", encoder.encode(36));
    }

    @Test
    void rejectsNegativeValues() {
        assertThrows(IllegalArgumentException.class, () -> encoder.encode(-1));
    }
}
