package com.dearjolly.server.global.version;

import com.dearjolly.server.global.version.enums.Platform;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** @param platforms 플랫폼별 재정의. 비어 있으면 공통 값을 그대로 쓴다. / */
@ConfigurationProperties(prefix = "dearjolly.version")
public record VersionProperties(
        String latest,
        String minSupported,
        String privacyPolicyUrl,
        String termsOfServiceUrl,
        String noticeUrl,
        Map<Platform, Override> platforms
) {
    public record Override(
            String latest,
            String minSupported
    ) {
    }

    public String latestFor(Platform platform) {
        return resolve(platform, Override::latest, latest);
    }

    public String minSupportedFor(Platform platform) {
        return resolve(platform, Override::minSupported, minSupported);
    }

    private String resolve(Platform platform, java.util.function.Function<Override, String> field, String fallback) {
        if (platform == null || platforms == null) {
            return fallback;
        }
        Override override = platforms.get(platform);
        if (override == null || field.apply(override) == null || field.apply(override).isBlank()) {
            return fallback;
        }
        return field.apply(override);
    }
}
