package com.dearjolly.server.domain.user.service;

import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_MAX_LENGTH;
import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_MIN_LENGTH;
import static com.dearjolly.server.domain.user.constants.UserValidationConstants.NICKNAME_PATTERN;

import com.dearjolly.server.domain.user.dto.request.NicknameUpdateRequest;
import com.dearjolly.server.domain.user.dto.request.TermsAgreeRequest;
import com.dearjolly.server.domain.user.dto.response.NicknameUpdateResponse;
import com.dearjolly.server.domain.user.dto.response.TermsAgreeResponse;
import com.dearjolly.server.domain.user.dto.response.UserGetResponse;
import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.oauth.OauthClientResolver;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService implements OnboardingChecker {

    private final UserRepository userRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final OauthClientResolver oauthClientResolver;
    private final TransactionTemplate transactionTemplate;

    @Value("${dearjolly.terms.current-version}")
    private String currentTermsVersion;

    /**
     * 약관 동의를 이력으로 누적한다. UPDATE 하지 않고 요청에 담긴 항목마다 INSERT 한다 (ERD §2.2).
     * 요청에 없는 항목은 건드리지 않으므로, 마케팅만 철회하려면 그 한 건만 보내면 된다.
     */
    @Transactional
    public TermsAgreeResponse agreeTerms(Long userId, TermsAgreeRequest request) {
        Users user = findActiveUser(userId);

        request.agreements().forEach(agreement ->
                termsAgreementRepository.save(
                        TermsAgreements.create(user, agreement.type(), agreement.agreed(), currentTermsVersion)
                )
        );

        boolean termsAgreed = TermsAgreementReader.isRequiredTermsAgreed(currentTermsState(userId));
        if (!termsAgreed) {
            throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
        }
        return TermsAgreeResponse.from(true);
    }

    public UserGetResponse getUser(Long userId) {
        Users user = findActiveUser(userId);
        boolean marketingAgreed = TermsAgreementReader.isMarketingAgreed(currentTermsState(userId));
        return UserGetResponse.of(user, marketingAgreed);
    }

    /**
     * 소셜 연결 해제는 트랜잭션 밖에서 먼저 수행한다. 외부 HTTP 호출이 DB 커넥션을
     * 응답 시간만큼 점유하지 않게 하기 위함이다 (기능명세 §3.1.3).
     *
     * <p>클래스 레벨 {@code readOnly = true} 를 그대로 두면 상태 변경이 저장되지 않고,
     * 같은 클래스의 {@code @Transactional} 메서드를 호출해도 프록시를 타지 않아 무시된다.
     * 그래서 이 메서드는 트랜잭션을 잠시 끊고, 쓰기 구간만 {@link TransactionTemplate} 으로 연다.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void withdraw(Long userId) {
        Users user = findActiveUser(userId);
        oauthClientResolver.resolve(user.getOauthProvider())
                .unlink(user.getOauthId(), user.getOauthRefreshToken());

        transactionTemplate.executeWithoutResult(status -> findActiveUser(userId).withdraw());
    }

    /**
     * 길이 → 문자 순서로 검증한다. 앱이 사유별로 다른 문구를 보여줘야 하므로
     * 두 조건을 동시에 어겨도 먼저 걸린 사유 하나만 반환한다 (기능명세 §3.2.2).
     */
    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        String nickname = request.nickname();
        validateNicknameLength(nickname);
        validateNicknameCharacter(nickname);

        Users user = findActiveUser(userId);
        user.updateNickname(nickname);
        return NicknameUpdateResponse.from(user);
    }

    @Override
    public boolean isOnboardingCompleted(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return user.isNicknameRegistered()
                && TermsAgreementReader.isRequiredTermsAgreed(currentTermsState(userId));
    }

    private Users findActiveUser(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (user.isWithdrawn()) {
            throw new BusinessException(ErrorCode.WITHDRAWN_USER);
        }
        return user;
    }

    private Map<TermsType, Boolean> currentTermsState(Long userId) {
        List<TermsAgreements> history = termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(userId);
        return TermsAgreementReader.toCurrentState(history);
    }

    /** null 과 빈 문자열은 0자로 본다. 공백만 있는 값은 길이를 통과하고 문자 검증에서 걸린다. */
    private void validateNicknameLength(String nickname) {
        int length = nickname == null ? 0 : nickname.codePointCount(0, nickname.length());
        if (length < NICKNAME_MIN_LENGTH || length > NICKNAME_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID_LENGTH);
        }
    }

    private void validateNicknameCharacter(String nickname) {
        if (!NICKNAME_PATTERN.matcher(nickname).matches()) {
            throw new BusinessException(ErrorCode.NICKNAME_INVALID_CHARACTER);
        }
    }
}
