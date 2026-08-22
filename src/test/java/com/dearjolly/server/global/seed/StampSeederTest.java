package com.dearjolly.server.global.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dearjolly.server.global.storage.ObjectStorage;
import java.io.IOException;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

@ExtendWith(MockitoExtension.class)
class StampSeederTest {
    private static final String LOCATION = "classpath*:seed/stamps/*.png";
    private static final String KEY_PREFIX = "stamp/";

    private static final long SEED_IMAGE_SIZE = 128L;

    @Mock
    private StampSeedWriter stampSeedWriter;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private ResourcePatternResolver resourcePatternResolver;

    @SuppressWarnings("unchecked")
    @DisplayName("파일명에서 확장자를 뗀 이름과 stamp/ 를 붙인 키로 시드를 만든다.")
    @Test
    void seed() throws IOException {
        // given
        givenResources("가방_쇼핑.png", "고양이.png");
        given(objectStorage.bucketExists()).willReturn(true);
        given(objectStorage.sizeOf(anyString())).willReturn(OptionalLong.empty());

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        ArgumentCaptor<List<StampSeed>> captor = ArgumentCaptor.forClass(List.class);
        verify(stampSeedWriter).write(captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new StampSeed("가방_쇼핑", "stamp/가방_쇼핑.png"),
                new StampSeed("고양이", "stamp/고양이.png")
        );
        verify(objectStorage).upload(eq("stamp/가방_쇼핑.png"), any(), anyLong(), eq("image/png"));
        verify(objectStorage).upload(eq("stamp/고양이.png"), any(), anyLong(), eq("image/png"));
    }

    @SuppressWarnings("unchecked")
    @DisplayName("기본 우표(soon)는 파일명 순서와 무관하게 항상 첫 번째로 시드한다.")
    @Test
    void seedPutsDefaultStampFirst() throws IOException {
        // given
        givenResources("가방_쇼핑.png", "soon.png", "고양이.png");
        given(objectStorage.bucketExists()).willReturn(true);
        given(objectStorage.sizeOf(anyString())).willReturn(OptionalLong.empty());

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        ArgumentCaptor<List<StampSeed>> captor = ArgumentCaptor.forClass(List.class);
        verify(stampSeedWriter).write(captor.capture());
        assertThat(captor.getValue()).extracting(StampSeed::name)
                .containsExactly("soon", "가방_쇼핑", "고양이");
    }

    @DisplayName("같은 키라도 크기가 다르면 이미지가 바뀐 것으로 보고 덮어쓴다.")
    @Test
    void seedReuploadsChangedObject() throws IOException {
        // given
        givenResources("고양이.png");
        given(objectStorage.bucketExists()).willReturn(true);
        given(objectStorage.sizeOf("stamp/고양이.png")).willReturn(OptionalLong.of(SEED_IMAGE_SIZE + 1));

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        verify(objectStorage).upload(eq("stamp/고양이.png"), any(), eq(SEED_IMAGE_SIZE), eq("image/png"));
    }

    @DisplayName("이미 올라가 있는 오브젝트는 다시 업로드하지 않고 DB 반영만 한다.")
    @Test
    void seedSkipsUploadedObject() throws IOException {
        // given
        givenResources("고양이.png");
        given(objectStorage.bucketExists()).willReturn(true);
        given(objectStorage.sizeOf("stamp/고양이.png")).willReturn(OptionalLong.of(SEED_IMAGE_SIZE));

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(stampSeedWriter).write(List.of(new StampSeed("고양이", "stamp/고양이.png")));
    }

    @DisplayName("시드가 비활성화되어 있으면 스토리지도 DB 도 건드리지 않는다.")
    @Test
    void seedDisabled() {
        // when
        seeder(false).run(new DefaultApplicationArguments());

        // then
        verifyNoInteractions(objectStorage, stampSeedWriter, resourcePatternResolver);
    }

    @DisplayName("버킷이 없으면 업로드도 DB 반영도 하지 않는다.")
    @Test
    void seedWithoutBucket() throws IOException {
        // given
        givenResources("고양이.png");
        given(objectStorage.bucketExists()).willReturn(false);

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        verify(objectStorage, never()).upload(anyString(), any(), anyLong(), anyString());
        verifyNoInteractions(stampSeedWriter);
    }

    private StampSeeder seeder(boolean enabled) {
        return new StampSeeder(
                new StampSeedProperties(enabled, LOCATION, KEY_PREFIX),
                stampSeedWriter,
                objectStorage,
                resourcePatternResolver
        );
    }

    private void givenResources(String... fileNames) throws IOException {
        Resource[] resources = new Resource[fileNames.length];
        for (int i = 0; i < fileNames.length; i++) {
            resources[i] = pngResource(fileNames[i]);
        }
        given(resourcePatternResolver.getResources(LOCATION)).willReturn(resources);
    }

    private Resource pngResource(String fileName) {
        return new ByteArrayResource(new byte[(int) SEED_IMAGE_SIZE]) {
            @Override
            public String getFilename() {
                return fileName;
            }
        };
    }
}
