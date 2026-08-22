package com.dearjolly.server.domain.letter.dto.response;

import java.util.List;

public record LetterListResponse(
        List<LetterSummaryResponse> letters,
        boolean hasNext
) {
    public static LetterListResponse of(List<LetterSummaryResponse> letters, boolean hasNext) {
        return new LetterListResponse(letters, hasNext);
    }
}
