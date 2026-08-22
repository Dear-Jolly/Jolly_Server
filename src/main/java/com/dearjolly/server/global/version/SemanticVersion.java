package com.dearjolly.server.global.version;

import static com.dearjolly.server.global.version.constants.VersionValidationConstants.VERSION_PATTERN;

import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;

/**
 * {@code x.y.z} 형식의 앱 버전. major → minor → patch 순으로 비교한다.
 */
public record SemanticVersion(int major, int minor, int patch) implements Comparable<SemanticVersion> {
    private static final String DELIMITER = "\\.";

    public static SemanticVersion parse(String value) {
        if (value == null || !VERSION_PATTERN.matcher(value).matches()) {
            throw new BusinessException(ErrorCode.APP_VERSION_INVALID, "version=" + value);
        }
        String[] parts = value.split(DELIMITER);
        return new SemanticVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        );
    }

    public boolean isOlderThan(SemanticVersion other) {
        return compareTo(other) < 0;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }
}
