package com.dearjolly.server.global.storage;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileUrlProvider {
    private final MinioProperties minioProperties;

    public String toPublicUrl(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            return null;
        }
        return trimTrailingSlash(minioProperties.publicEndpoint())
                + "/" + minioProperties.bucket()
                + "/" + encodePath(trimLeadingSlash(fileKey));
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }

    // 우표 파일 키는 한글이라 그대로 붙이면 URL 로 성립하지 않는다. 구분자(/)는 남기고 세그먼트만 인코딩한다.
    private String encodePath(String fileKey) {
        return Arrays.stream(fileKey.split("/", -1))
                .map(this::encodeSegment)
                .collect(Collectors.joining("/"));
    }

    private String encodeSegment(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
