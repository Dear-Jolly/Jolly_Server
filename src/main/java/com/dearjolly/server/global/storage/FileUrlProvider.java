package com.dearjolly.server.global.storage;

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
                + "/" + trimLeadingSlash(fileKey);
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
