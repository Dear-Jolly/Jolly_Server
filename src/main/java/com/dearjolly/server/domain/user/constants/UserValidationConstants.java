package com.dearjolly.server.domain.user.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UserValidationConstants {

    public static final int NICKNAME_MIN_LENGTH = 2;
    public static final int NICKNAME_MAX_LENGTH = 10;
    public static final String NICKNAME_REGEX = "^[a-zA-Z0-9]+$";
}
