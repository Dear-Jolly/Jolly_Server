package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.user.enums.OauthProvider.KAKAO;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

@ExtendWith(MockitoExtension.class)
class MockUserSeederTest {
    @Mock
    private MockUserSeedWriter mockUserSeedWriter;

    @DisplayName("시드가 켜져 있으면 목 사용자 시드 데이터를 그대로 넘긴다.")
    @Test
    void seed() {
        // given
        given(mockUserSeedWriter.write(any(), any())).willReturn(new MockUserSeedResult(1L, 6, 4));

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        verify(mockUserSeedWriter).write(properties(true), MockUserSeedData.LETTERS);
    }

    @DisplayName("시드가 비활성화되어 있으면 DB 를 건드리지 않는다.")
    @Test
    void seedDisabled() {
        // when
        seeder(false).run(new DefaultApplicationArguments());

        // then
        verifyNoInteractions(mockUserSeedWriter);
    }

    @DisplayName("시드가 실패해도 예외를 밖으로 던지지 않아 기동을 막지 않는다.")
    @Test
    void seedSwallowsFailure() {
        // given
        given(mockUserSeedWriter.write(any(), any())).willThrow(new IllegalStateException("boom"));

        // when & then
        assertThatCode(() -> seeder(true).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    private MockUserSeeder seeder(boolean enabled) {
        return new MockUserSeeder(properties(enabled), mockUserSeedWriter);
    }

    private MockUserSeedProperties properties(boolean enabled) {
        return new MockUserSeedProperties(enabled, KAKAO, "mock-user", "mock@dearjolly.local", "jolly");
    }
}
