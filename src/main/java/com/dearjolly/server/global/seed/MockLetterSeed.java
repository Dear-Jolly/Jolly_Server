package com.dearjolly.server.global.seed;

import java.util.List;

public record MockLetterSeed(
        int daysAgo,
        String content,
        String correctedContent,
        List<String> tips,
        String stampName,
        boolean read
) {
    public static MockLetterSeed completed(
            int daysAgo, String content, String correctedContent, List<String> tips, String stampName, boolean read
    ) {
        return new MockLetterSeed(daysAgo, content, correctedContent, tips, stampName, read);
    }

    public static MockLetterSeed pending(int daysAgo, String content) {
        return new MockLetterSeed(daysAgo, content, null, List.of(), null, false);
    }

    public boolean isFeedbackCompleted() {
        return correctedContent != null;
    }
}
