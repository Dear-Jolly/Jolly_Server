package com.dearjolly.server.domain.user.constants;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserValidationConstants {
    public static final int NICKNAME_MIN_LENGTH = 1;
    public static final int NICKNAME_MAX_LENGTH = 20;

    public static final String NICKNAME_REGEX = "^[A-Za-z0-9]+$";

    public static final Pattern NICKNAME_PATTERN = Pattern.compile(NICKNAME_REGEX);
}
