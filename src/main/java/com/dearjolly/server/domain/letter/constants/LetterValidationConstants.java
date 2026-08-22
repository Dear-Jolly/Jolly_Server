package com.dearjolly.server.domain.letter.constants;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LetterValidationConstants {
    public static final int CONTENT_MAX_LENGTH = 500;

    public static final String KOREAN_REGEX = "[가-힣ㄱ-ㅎㅏ-ㅣ]";

    public static final Pattern KOREAN_PATTERN = Pattern.compile(KOREAN_REGEX);

    public static final int WRITTEN_AT_TOLERANCE_HOURS = 24;

    public static final int DUPLICATE_WINDOW_SECONDS = 60;

    public static final int STAMP_NAME_MAX_LENGTH = 30;
}
