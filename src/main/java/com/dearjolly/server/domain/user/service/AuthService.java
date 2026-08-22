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
    private final JwtProvider jwtProvider;

    /** provider 의 로그인 페이지 URL. 컨트롤러가 이 주소로 302 리다이렉트한다. */
    public String buildAuthorizationUri(OauthProvider provider, String state) {
        return oauthClientResolver.resolve(provider).buildAuthorizationUri(state);
    }

    /**
     * 콜백으로 받은 authorization code 를 교환해 회원을 찾거나 만들고 JWT 를 발급한다.
     * 탈퇴 유예기간 중인 계정이 걸리면 식별자를 치환해 UNIQUE 를 비우고 신규 가입으로 처리한다
     * (기능명세 §3.1.1).
     */
    @Transactional
    public OauthLoginResult handleCallback(OauthProvider provider, String code, String idToken) {
        OauthUserInfo userInfo = oauthClientResolver.resolve(provider).exchange(code, idToken);

        Optional<Users> found = userRepository.findByOauthProviderAndOauthId(
                userInfo.provider(), userInfo.oauthId());

        boolean isNewUser = found.isEmpty() || found.get().isWithdrawn();
        Users user = found.filter(existing -> !existing.isWithdrawn())
                .orElseGet(() -> registerNewUser(found.orElse(null), userInfo));

        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getRole());
        String refreshToken = jwtProvider.createRefreshToken(user.getId(), user.getRole());
        user.updateRefreshToken(refreshToken);
        user.updateOauthRefreshToken(userInfo.oauthRefreshToken());

        return OauthLoginResult.of(user, accessToken, refreshToken, isNewUser, isRequiredTermsAgreed(user.getId()));
    }

    /**
     * Access / Refresh 를 모두 새로 발급하고 저장값을 교체(회전)한다.
     * 저장값과 문자열까지 일치하지 않으면 탈취된 이전 토큰의 재사용으로 보고 거절한다.
     */
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

    /** 세션만 끊는다. 편지·계정 데이터는 그대로 보존된다. */
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
