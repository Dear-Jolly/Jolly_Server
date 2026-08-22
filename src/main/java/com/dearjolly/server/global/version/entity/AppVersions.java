package com.dearjolly.server.global.version.entity;

import static com.dearjolly.server.global.version.constants.VersionValidationConstants.VERSION_MAX_LENGTH;
import static com.dearjolly.server.global.version.constants.VersionValidationConstants.VERSION_PATTERN;

import com.dearjolly.server.global.version.enums.Platform;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "APP_VERSIONS")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppVersions {
    // 플랫폼마다 행이 하나뿐이라 대리키를 두지 않는다. 플랫폼 자체가 식별자다.
    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 10)
    private Platform platform;

    @Column(name = "min_supported_version", nullable = false, length = VERSION_MAX_LENGTH)
    private String minSupportedVersion;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }

    public static AppVersions create(Platform platform, String minSupportedVersion) {
        return new AppVersions(platform, minSupportedVersion);
    }

    public void updateMinSupportedVersion(String minSupportedVersion) {
        validateMinSupportedVersion(minSupportedVersion);
        this.minSupportedVersion = minSupportedVersion;
    }

    private AppVersions(Platform platform, String minSupportedVersion) {
        validatePlatform(platform);
        validateMinSupportedVersion(minSupportedVersion);
        this.platform = platform;
        this.minSupportedVersion = minSupportedVersion;
    }

    private void validatePlatform(Platform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("플랫폼은 필수입니다.");
        }
    }

    private void validateMinSupportedVersion(String minSupportedVersion) {
        if (minSupportedVersion == null || !VERSION_PATTERN.matcher(minSupportedVersion).matches()) {
            throw new IllegalArgumentException("최소 지원 버전은 x.y.z 형식이어야 합니다: " + minSupportedVersion);
        }
    }
}
