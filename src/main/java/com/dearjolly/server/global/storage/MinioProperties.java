package com.dearjolly.server.global.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MinIO 접속 정보. 값은 전부 환경변수에서 주입된다 (compose 의 .env).
 *
 * <p>endpoint 는 서버 → MinIO 내부 호출용이고, publicEndpoint 는 클라이언트에게
 * 내려줄 이미지 URL 의 기준이다. 컨테이너 내부 주소와 외부 공개 주소가 다르기 때문에
 * 두 값을 분리해서 갖는다.
 */
@ConfigurationProperties(prefix = "minio")
public record MinioProperties(
        String endpoint,
        String publicEndpoint,
        String accessKey,
        String secretKey,
        String bucket
) {
}
