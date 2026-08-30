package com.dearjolly.server.global.logging;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogValueSanitizer {
    private static final int MAX_LENGTH = 500;

    public static String sanitize(Object value) {
        if (value == null) {
            return "none";
        }
        String sanitized = value.toString()
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace('\t', ' ');
        return sanitized.length() <= MAX_LENGTH ? sanitized : sanitized.substring(0, MAX_LENGTH);
    }
}
