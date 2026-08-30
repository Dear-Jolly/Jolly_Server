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
class UserSeederTest {
    @Mock
    private UserSeedWriter userSeedWriter;

    @DisplayName("시드가 켜져 있으면 관리자 계정을 생성한다.")
    @Test
    void seed() {
        // given
        given(userSeedWriter.write(any())).willReturn(1L);

        // when
        seeder(true).run(new DefaultApplicationArguments());

        // then
        verify(userSeedWriter).write(properties(true));
    }

    @DisplayName("시드가 비활성화되어 있으면 DB 를 건드리지 않는다.")
    @Test
    void seedDisabled() {
        // when
        seeder(false).run(new DefaultApplicationArguments());

        // then
        verifyNoInteractions(userSeedWriter);
    }

    @DisplayName("시드가 실패해도 예외를 밖으로 던지지 않아 기동을 막지 않는다.")
    @Test
    void seedSwallowsFailure() {
        // given
        given(userSeedWriter.write(any())).willThrow(new IllegalStateException("boom"));

        // when & then
        assertThatCode(() -> seeder(true).run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    private UserSeeder seeder(boolean enabled) {
        return new UserSeeder(properties(enabled), userSeedWriter);
    }

    private UserSeedProperties properties(boolean enabled) {
        return new UserSeedProperties(enabled, KAKAO, "seed-admin", "admin@dearjolly.local", "jolly");
    }
}
