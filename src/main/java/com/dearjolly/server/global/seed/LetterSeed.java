package com.dearjolly.server.global.seed;

import java.util.List;

public record LetterSeed(
        int daysAgo,
        String content,
        String correctedContent,
        List<String> tips,
        String stampName,
        boolean read
) {
    public static LetterSeed completed(
            int daysAgo, String content, String correctedContent, List<String> tips, String stampName, boolean read
    ) {
        return new LetterSeed(daysAgo, content, correctedContent, tips, stampName, read);
    }

    public static LetterSeed pending(int daysAgo, String content) {
        return new LetterSeed(daysAgo, content, null, List.of(), null, false);
    }

    public boolean isFeedbackCompleted() {
        return correctedContent != null;
    }
}
