package com.dearjolly.server.global.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 파일 키를 공개 URL 로 조립한다.
 *
 * <p>DB 에는 파일 키만 저장하고 URL 은 저장하지 않는다 (ERD §2.4).
 * 스토리지 주소는 환경마다 다르고(로컬 {@code localhost:9000}, 운영 EC2 공인 IP)
 * 도메인 교체·CDN 도입 시 바뀌는데, URL 을 저장해 두면 그때마다 전체 행을 일괄 수정해야 한다.
 * 파일 키만 두면 환경변수만 갈아끼우면 된다.
 */
@Component
@RequiredArgsConstructor
public class FileUrlProvider {

    private final MinioProperties minioProperties;

    /** 키가 비어 있으면 null 을 반환한다. 우표가 부여되지 않은 편지의 stampImage 가 null 이어야 하기 때문이다. */
    public String toPublicUrl(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        return trimTrailingSlash(minioProperties.publicEndpoint())
                + "/" + minioProperties.bucket()
                + "/" + trimLeadingSlash(fileKey);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
