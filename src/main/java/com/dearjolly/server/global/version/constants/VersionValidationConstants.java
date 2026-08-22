package com.dearjolly.server.global.version.constants;

import java.util.regex.Pattern;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VersionValidationConstants {
    public static final String VERSION_REGEX = "^\\d{1,5}\\.\\d{1,5}\\.\\d{1,5}$";

    public static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEX);

    public static final int VERSION_MAX_LENGTH = 20;

    public static final String DEFAULT_MIN_SUPPORTED_VERSION = "1.0.0";
}
