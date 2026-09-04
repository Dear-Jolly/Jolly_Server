package com.dearjolly.server.domain.user.service;

import static com.dearjolly.server.domain.user.enums.OauthProvider.APPLE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.auth.oauth.OauthClient;
import com.dearjolly.server.global.auth.oauth.OauthClientResolver;
import com.dearjolly.server.global.auth.oauth.OauthStateProvider;
import com.dearjolly.server.global.auth.oauth.OauthUserInfo;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 애플 revoke 는 저장해 둔 oauth refresh token 으로만 할 수 있다.
 * 재로그인 때 애플이 값을 주지 않는 경우가 있어, 그때 기존 값을 지워버리면
 * 탈퇴 시점에 revoke 할 수단이 영영 사라진다.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceOauthRefreshTokenTest {
    private static final String OAUTH_ID = "apple-sub";
    private static final String STORED = "stored-apple-refresh";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TermsAgreementRepository termsAgreementRepository;
    @Mock
    private OauthClientResolver oauthClientResolver;
    @Mock
    private OauthStateProvider oauthStateProvider;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private OauthClient appleClient;

    private AuthService authService;
    private Users user;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository, termsAgreementRepository, oauthClientResolver, oauthStateProvider, jwtProvider);

        user = Users.create(APPLE, OAUTH_ID, "user@privaterelay.appleid.com");
        user.updateOauthRefreshToken(STORED);

        when(oauthClientResolver.resolve(APPLE)).thenReturn(appleClient);
        when(userRepository.findByOauthProviderAndOauthId(APPLE, OAUTH_ID)).thenReturn(Optional.of(user));
        when(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(any())).thenReturn(List.of());
        when(jwtProvider.createAccessToken(any(), any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any(), any())).thenReturn("refresh");
    }

    @DisplayName("애플이 refresh token 을 주지 않은 재로그인에서는 저장된 값을 유지한다")
    @Test
    void keepsStoredTokenWhenProviderOmitsIt() {
        when(appleClient.exchange(any(), any()))
                .thenReturn(new OauthUserInfo(APPLE, OAUTH_ID, null, null));

        authService.handleCallback(APPLE, "code", "id-token", "state");

        assertThat(user.getOauthRefreshToken())
                .as("null 로 덮으면 탈퇴 때 애플 revoke 를 할 수 없게 된다")
                .isEqualTo(STORED);
    }

    @DisplayName("빈 문자열이 와도 저장된 값을 유지한다")
    @Test
    void keepsStoredTokenWhenProviderSendsBlank() {
        when(appleClient.exchange(any(), any()))
                .thenReturn(new OauthUserInfo(APPLE, OAUTH_ID, null, "  "));

        authService.handleCallback(APPLE, "code", "id-token", "state");

        assertThat(user.getOauthRefreshToken()).isEqualTo(STORED);
    }

    @DisplayName("애플이 새 refresh token 을 주면 갱신한다")
    @Test
    void replacesStoredTokenWhenProviderSendsNewOne() {
        when(appleClient.exchange(any(), any()))
                .thenReturn(new OauthUserInfo(APPLE, OAUTH_ID, null, "fresh-apple-refresh"));

        authService.handleCallback(APPLE, "code", "id-token", "state");

        assertThat(user.getOauthRefreshToken()).isEqualTo("fresh-apple-refresh");
    }
}
