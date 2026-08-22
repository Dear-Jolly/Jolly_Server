package com.dearjolly.server.global.seed;

import static com.dearjolly.server.global.version.constants.VersionValidationConstants.DEFAULT_MIN_SUPPORTED_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.repository.AppVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class AppVersionSeederTest {
    @Mock
    private AppVersionRepository appVersionRepository;

    @DisplayName("행이 없는 플랫폼만 기본 최소 지원 버전으로 채운다.")
    @Test
    void seed() {
        // given
        given(appVersionRepository.existsById(any())).willReturn(false);

        // when
        int created = writer().write();

        // then
        assertThat(created).isEqualTo(Platform.values().length);
        ArgumentCaptor<AppVersions> captor = ArgumentCaptor.forClass(AppVersions.class);
        verify(appVersionRepository, times(Platform.values().length)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(AppVersions::getPlatform)
                .containsExactly(Platform.values());
        assertThat(captor.getAllValues())
                .allSatisfy(appVersion -> assertThat(appVersion.getMinSupportedVersion())
                        .isEqualTo(DEFAULT_MIN_SUPPORTED_VERSION));
    }

    @DisplayName("이미 있는 행은 덮어쓰지 않는다. 값의 주인은 관리자 API 다.")
    @Test
    void seedKeepsExistingRow() {
        // given
        given(appVersionRepository.existsById(any())).willReturn(true);

        // when
        int created = writer().write();

        // then
        assertThat(created).isZero();
        verify(appVersionRepository, never()).save(any());
    }

    @DisplayName("시드가 비활성화되어 있으면 DB 를 건드리지 않는다.")
    @Test
    void seedDisabled() {
        // when
        seeder(false).run(new DefaultApplicationArguments());

        // then
        verifyNoInteractions(appVersionRepository);
    }

    @DisplayName("시드가 실패해도 예외를 밖으로 던지지 않아 기동을 막지 않는다.")
    @Test
    void seedSwallowsFailure() {
        // given
        given(appVersionRepository.existsById(any())).willThrow(new IllegalStateException("boom"));

        // when & then
        assertThatCode(() -> seeder(true).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    private AppVersionSeeder seeder(boolean enabled) {
        return new AppVersionSeeder(new AppVersionSeedProperties(enabled), writer());
    }

    private AppVersionSeedWriter writer() {
        return new AppVersionSeedWriter(appVersionRepository);
    }
}
