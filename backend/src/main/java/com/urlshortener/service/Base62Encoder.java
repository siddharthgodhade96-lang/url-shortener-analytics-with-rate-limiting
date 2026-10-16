package com.urlshortener.service;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int RADIX = ALPHABET.length;

    public String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must not be negative");
        }
        if (value == 0) {
            return "0";
        }

        StringBuilder encoded = new StringBuilder();
        while (value > 0) {
            encoded.append(ALPHABET[(int) (value % RADIX)]);
            value /= RADIX;
        }
        return encoded.reverse().toString();
    }
}
