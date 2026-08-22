package com.dearjolly.server.global.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FileUrlProviderTest {

    private static final String PUBLIC_ENDPOINT = "http://3.35.1.2:9000";
    private static final String BUCKET = "dear-jolly-stamps";

    private final FileUrlProvider fileUrlProvider = new FileUrlProvider(
            new MinioProperties("http://minio:9000", PUBLIC_ENDPOINT, "key", "secret", BUCKET));

    @DisplayName("파일 키 앞에 공개 엔드포인트와 버킷을 붙여 URL 을 만든다.")
    @Test
    void toPublicUrl() {
        // given
        String fileKey = "stamps/rose.png";

        // when
        String url = fileUrlProvider.toPublicUrl(fileKey);

        // then
        assertThat(url).isEqualTo("http://3.35.1.2:9000/dear-jolly-stamps/stamps/rose.png");
    }

    @DisplayName("엔드포인트 끝과 파일 키 앞의 슬래시가 겹쳐도 URL 이 깨지지 않는다.")
    @Test
    void toPublicUrlWithRedundantSlash() {
        // given
        FileUrlProvider provider = new FileUrlProvider(
                new MinioProperties("http://minio:9000", PUBLIC_ENDPOINT + "/", "key", "secret", BUCKET));

        // when
        String url = provider.toPublicUrl("/stamps/rose.png");

        // then
        assertThat(url).isEqualTo("http://3.35.1.2:9000/dear-jolly-stamps/stamps/rose.png");
    }

    @DisplayName("파일 키가 없으면 URL 도 없으므로 null 을 반환한다.")
    @Test
    void toPublicUrlWithoutFileKey() {
        // when & then
        assertThat(fileUrlProvider.toPublicUrl(null)).isNull();
        assertThat(fileUrlProvider.toPublicUrl(" ")).isNull();
    }
}
