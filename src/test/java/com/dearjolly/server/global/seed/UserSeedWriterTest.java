package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.user.enums.OauthProvider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.Role;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserSeedWriterTest {
    private static final String OAUTH_ID = "seed-admin";
    private static final String NICKNAME = "jolly";

    @Mock
    private UserRepository userRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    private UserSeedWriter userSeedWriter;

    @BeforeEach
    void setUp() {
        userSeedWriter = new UserSeedWriter(userRepository, termsAgreementRepository);
        ReflectionTestUtils.setField(userSeedWriter, "currentTermsVersion", "1.0.0");
        given(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(anyLong())).willReturn(List.of());
    }

    @DisplayName("시드 User 가 없으면 관리자 계정과 약관 동의를 만든다.")
    @Test
    void write() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        Users saved = persistedUser();
        given(userRepository.save(any())).willReturn(saved);

        // when
        Long userId = userSeedWriter.write(properties());

        // then
        assertThat(userId).isEqualTo(1L);
        assertThat(saved.getNickname()).isEqualTo(NICKNAME);
        verify(termsAgreementRepository, times(TermsType.values().length)).save(any());
    }

    @DisplayName("시드 User 는 관리자 권한으로 만든다.")
    @Test
    void writeCreatesAdminUser() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        given(userRepository.save(any())).willReturn(persistedUser());

        // when
        userSeedWriter.write(properties());

        // then
        ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @DisplayName("탈퇴한 시드 User 가 남아 있으면 소셜 식별자를 풀고 계정을 다시 만든다.")
    @Test
    void writeAfterWithdrawal() {
        // given
        Users withdrawn = persistedUser();
        withdrawn.withdraw();
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.of(withdrawn));
        Users rejoined = persistedUser();
        given(userRepository.save(any())).willReturn(rejoined);

        // when
        Long userId = userSeedWriter.write(properties());

        // then
        verify(userRepository).saveAndFlush(withdrawn);
        assertThat(withdrawn.getOauthId()).startsWith(OAUTH_ID).isNotEqualTo(OAUTH_ID);
        assertThat(userId).isEqualTo(1L);
    }

    @DisplayName("이미 동의한 약관은 다시 남기지 않는다.")
    @Test
    void writeSkipsAgreedTerms() {
        // given
        Users existing = persistedUser();
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.of(existing));
        given(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(anyLong()))
                .willReturn(List.of(agreement(existing, TermsType.SERVICE)));

        // when
        userSeedWriter.write(properties());

        // then
        verify(termsAgreementRepository, times(TermsType.values().length - 1)).save(any());
    }

    private UserSeedProperties properties() {
        return new UserSeedProperties(true, KAKAO, OAUTH_ID, "admin@dearjolly.local", NICKNAME);
    }

    private Users persistedUser() {
        Users user = Users.createAdmin(KAKAO, OAUTH_ID, "admin@dearjolly.local");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private TermsAgreements agreement(Users user, TermsType type) {
        return TermsAgreements.create(user, type, true, "1.0.0");
    }
}
