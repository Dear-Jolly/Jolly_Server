package com.dearjolly.server.global.version;

import com.dearjolly.server.global.version.enums.Platform;
import java.util.Map;
import java.util.function.Function;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param platforms 플랫폼별 재정의. 비어 있으면 공통 값을 그대로 쓴다. / */
@ConfigurationProperties(prefix = "dearjolly.version")
public record VersionProperties(
        String latest,
        String minSupported,
        Boolean forceUpdate,
        String privacyPolicyUrl,
        String termsOfServiceUrl,
        String noticeUrl,
        Map<Platform, Override> platforms
) {
    public record Override(
            String latest,
            String minSupported,
            Boolean forceUpdate
    ) {
    }

    public String latestFor(Platform platform) {
        String value = override(platform, Override::latest);
        return isBlank(value) ? latest : value;
    }

    public String minSupportedFor(Platform platform) {
        String value = override(platform, Override::minSupported);
        return isBlank(value) ? minSupported : value;
    }

    public boolean forceUpdateFor(Platform platform) {
        Boolean value = override(platform, Override::forceUpdate);
        if (value != null) {
            return value;
        }
        return forceUpdate != null && forceUpdate;
    }

    private <T> T override(Platform platform, Function<Override, T> field) {
        if (platform == null || platforms == null) {
            return null;
        }
        Override found = platforms.get(platform);
        return found == null ? null : field.apply(found);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
