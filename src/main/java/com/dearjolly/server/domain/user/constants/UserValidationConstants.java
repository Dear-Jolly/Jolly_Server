package com.dearjolly.server.domain.user.constants;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserValidationConstants {

    public static final int NICKNAME_MIN_LENGTH = 1;
    public static final int NICKNAME_MAX_LENGTH = 20;

    /** 길이 수량자를 넣지 않는다. 길이 위반(USER_004)과 문자 위반(USER_003)을 구분해야 하기 때문이다. */
    public static final String NICKNAME_REGEX = "^[A-Za-z0-9]+$";

    public static final Pattern NICKNAME_PATTERN = Pattern.compile(NICKNAME_REGEX);
}
