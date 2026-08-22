package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import java.time.LocalDate;

public record LetterSummaryResponse(
        Long letterId,
        LocalDate date,
        String summary,
        Status status,
        boolean isRead,
        String stampImage
) {
    private static final int SUMMARY_LENGTH = 50;

    public static LetterSummaryResponse of(Letters letter, String stampImage) {
        return new LetterSummaryResponse(
                letter.getId(),
                letter.getLetterDate(),
                summarize(letter.getContent()),
                letter.toResponseStatus(),
                letter.isRead(),
                stampImage
        );
    }

    private static String summarize(String content) {
        int codePointCount = content.codePointCount(0, content.length());
        if (codePointCount <= SUMMARY_LENGTH) {
            return content;
        }
        return content.substring(0, content.offsetByCodePoints(0, SUMMARY_LENGTH));
    }
}
