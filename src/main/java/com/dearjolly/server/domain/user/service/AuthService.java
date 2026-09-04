package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.dto.request.ReissueRequest;
import com.dearjolly.server.domain.user.dto.response.OauthLoginResult;
import com.dearjolly.server.domain.user.dto.response.ReissueResponse;
import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.auth.oauth.OauthClientResolver;
import com.dearjolly.server.global.auth.oauth.OauthStateProvider;
import com.dearjolly.server.global.auth.oauth.OauthUserInfo;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import io.jsonwebtoken.JwtException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository userRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final OauthClientResolver oauthClientResolver;
    private final OauthStateProvider oauthStateProvider;
    private final JwtProvider jwtProvider;

    public String buildAuthorizationUri(OauthProvider provider) {
        return oauthClientResolver.resolve(provider).buildAuthorizationUri(oauthStateProvider.issue(provider));
    }

    @Transactional
    public OauthLoginResult handleCallback(OauthProvider provider, String code, String idToken, String state) {
        oauthStateProvider.validate(provider, state);

        OauthUserInfo userInfo = oauthClientResolver.resolve(provider).exchange(code, idToken);

        Optional<Users> found = userRepository.findByOauthProviderAndOauthId(
                userInfo.provider(), userInfo.oauthId());

        boolean isNewUser = found.isEmpty() || found.get().isWithdrawn();
        Users user = found.filter(existing -> !existing.isWithdrawn())
                .orElseGet(() -> registerNewUser(found.orElse(null), userInfo));

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);
        // provider 가 이번 로그인에서 refresh token 을 주지 않았다면 기존 값을 그대로 둔다.
        // null 로 덮으면 탈퇴 때 revoke 할 수단이 영영 사라진다.
        if (userInfo.oauthRefreshToken() != null && !userInfo.oauthRefreshToken().isBlank()) {
            user.updateOauthRefreshToken(userInfo.oauthRefreshToken());
        }

        return OauthLoginResult.of(user, accessToken, refreshToken, isNewUser, isRequiredTermsAgreed(user.getId()));
    }

    @Transactional
    public ReissueResponse reissue(ReissueRequest request) {
        String presented = request.refreshToken();
        Long userId = extractUserId(presented);

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (user.isWithdrawn() || !presented.equals(user.getRefreshToken())) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);

        return ReissueResponse.of(accessToken, refreshToken);
    }

    @Transactional
    public void logout(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        user.clearRefreshToken();
    }

    private Users registerNewUser(Users withdrawnUser, OauthUserInfo userInfo) {
        if (withdrawnUser != null) {
            withdrawnUser.releaseOauthIdForRejoin();
            userRepository.saveAndFlush(withdrawnUser);
        }
        return userRepository.save(
                Users.create(userInfo.provider(), userInfo.oauthId(), userInfo.email())
        );
    }

    private Long extractUserId(String refreshToken) {
        try {
            return jwtProvider.getUserId(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private boolean isRequiredTermsAgreed(Long userId) {
        List<TermsAgreements> history = termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(userId);
        Map<TermsType, Boolean> current = TermsAgreementReader.toCurrentState(history);
        return TermsAgreementReader.isRequiredTermsAgreed(current);
    }
}
