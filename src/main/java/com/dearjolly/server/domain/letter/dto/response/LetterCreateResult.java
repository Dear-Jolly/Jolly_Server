package com.dearjolly.server.domain.letter.dto.response;

import com.dearjolly.server.domain.letter.entity.Letters;

public record LetterCreateResult(
        LetterCreateResponse response,
        boolean created
) {
    public static LetterCreateResult created(Letters letter) {
        return new LetterCreateResult(LetterCreateResponse.from(letter), true);
    }

    public static LetterCreateResult duplicated(Letters letter) {
        return new LetterCreateResult(LetterCreateResponse.from(letter), false);
    }
}
