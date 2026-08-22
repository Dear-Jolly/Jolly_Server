package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.STAMP_NAME_MAX_LENGTH;
import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;

import com.dearjolly.server.global.storage.ObjectStorage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(SeedOrder.STAMP)
@RequiredArgsConstructor
public class StampSeeder implements ApplicationRunner {
    private static final String IMAGE_EXTENSION = ".png";

    private static final String IMAGE_CONTENT_TYPE = "image/png";

    private final StampSeedProperties stampSeedProperties;
    private final StampSeedWriter stampSeedWriter;
    private final ObjectStorage objectStorage;
    private final ResourcePatternResolver resourcePatternResolver;

    @Override
    public void run(ApplicationArguments args) {
        if (!stampSeedProperties.enabled()) {
            log.info("우표 시드가 비활성화되어 있어 건너뛴다.");
            return;
        }
        try {
            seed();
        } catch (Exception e) {
            // 시드 실패로 기동을 막으면 무중단 배포가 통째로 멈춘다. 로그만 남기고 기동은 이어간다.
            log.error("우표 시드에 실패했다.", e);
        }
    }

    private void seed() throws IOException {
        List<Resource> resources = loadResources();
        if (resources.isEmpty()) {
            log.warn("우표 시드 이미지가 없다. location={}", stampSeedProperties.location());
            return;
        }
        if (!objectStorage.bucketExists()) {
            log.error("우표 시드 버킷이 없어 중단한다. 버킷 생성(minio-init) 이후 다시 기동한다.");
            return;
        }

        List<StampSeed> seeds = new ArrayList<>();
        int uploaded = 0;
        for (Resource resource : resources) {
            String fileName = fileNameOf(resource);
            String imageKey = stampSeedProperties.keyPrefix() + fileName;
            if (upload(resource, imageKey)) {
                uploaded++;
            }
            seeds.add(new StampSeed(stampNameOf(fileName), imageKey));
        }
        int changed = stampSeedWriter.write(seeds);

        log.info("우표 시드 완료. 대상 {}건, 업로드 {}건, DB 반영 {}건", seeds.size(), uploaded, changed);
    }

    // 기본 우표를 맨 앞에 두어 stamp_id = 1 로 들어가게 한다. 나머지는 파일명 순으로 고정한다.
    private List<Resource> loadResources() throws IOException {
        Comparator<Resource> defaultFirst = Comparator
                .comparingInt((Resource resource) -> isDefaultStamp(resource) ? 0 : 1)
                .thenComparing(this::fileNameOf);
        return Arrays.stream(resourcePatternResolver.getResources(stampSeedProperties.location()))
                .sorted(defaultFirst)
                .toList();
    }

    private boolean isDefaultStamp(Resource resource) {
        return DEFAULT_STAMP_NAME.equals(stampNameOf(fileNameOf(resource)));
    }

    // 같은 키에 같은 크기로 이미 올라가 있으면 건너뛴다. 크기가 다르면 이미지가 교체된 것으로 보고 덮어쓴다.
    // MinIO 의 putObject 는 같은 키에 덮어쓰기라, 중간에 죽어도 다음 기동에서 같은 상태로 수렴한다.
    private boolean upload(Resource resource, String imageKey) throws IOException {
        long size = resource.contentLength();
        OptionalLong uploadedSize = objectStorage.sizeOf(imageKey);
        if (uploadedSize.isPresent() && uploadedSize.getAsLong() == size) {
            return false;
        }
        try (InputStream content = resource.getInputStream()) {
            objectStorage.upload(imageKey, content, size, IMAGE_CONTENT_TYPE);
        }
        return true;
    }

    private String fileNameOf(Resource resource) {
        String fileName = resource.getFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalStateException("우표 시드 이미지의 파일명을 읽을 수 없다: " + resource.getDescription());
        }
        return fileName;
    }

    private String stampNameOf(String fileName) {
        String name = fileName.substring(0, fileName.length() - IMAGE_EXTENSION.length());
        if (name.isBlank() || name.length() > STAMP_NAME_MAX_LENGTH) {
            throw new IllegalStateException("우표 이름이 STAMPS.name 길이 제한을 벗어난다: " + fileName);
        }
        return name;
    }
}
