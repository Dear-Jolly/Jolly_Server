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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void withdraw(Long userId) {
        Users user = findActiveUser(userId);
        oauthClientResolver.resolve(user.getOauthProvider())
                .unlink(user.getOauthId(), user.getOauthRefreshToken());

        transactionTemplate.executeWithoutResult(status -> findActiveUser(userId).withdraw());
    }

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
    public boolean isOnboardingCompleted(Users user) {
        return user.isNicknameRegistered()
                && TermsAgreementReader.isRequiredTermsAgreed(currentTermsState(user.getId()));
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
