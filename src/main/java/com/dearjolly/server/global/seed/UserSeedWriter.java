package com.dearjolly.server.global.seed;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSeedWriter {
    private final UserRepository userRepository;
    private final TermsAgreementRepository termsAgreementRepository;

    @Value("${dearjolly.terms.current-version}")
    private String currentTermsVersion;

    @Transactional
    public Long write(UserSeedProperties properties) {
        Users user = findOrCreateUser(properties);
        agreeAllTerms(user);
        return user.getId();
    }

    // 탈퇴 API 를 시험하고 나면 같은 소셜 식별자가 탈퇴 계정에 묶여 있다. 실제 재가입과 같은 방식으로
    // 식별자를 풀어 주고 새 계정을 만들어야, 시드를 다시 돌리는 것만으로 테스트를 이어갈 수 있다.
    private Users findOrCreateUser(UserSeedProperties properties) {
        Optional<Users> found = userRepository.findByOauthProviderAndOauthId(
                properties.oauthProvider(), properties.oauthId());

        Users user = found.filter(existing -> !existing.isWithdrawn())
                .orElseGet(() -> createUser(found.orElse(null), properties));
        if (!user.isNicknameRegistered()) {
            user.updateNickname(properties.nickname());
        }
        return user;
    }

    private Users createUser(Users withdrawnUser, UserSeedProperties properties) {
        if (withdrawnUser != null) {
            withdrawnUser.releaseOauthIdForRejoin();
            userRepository.saveAndFlush(withdrawnUser);
        }
        // 관리자는 회원가입 경로가 없어 이 시드가 유일한 생성 지점이다. 소셜 로그인 없이 편지·홈 API 까지
        // 그대로 두드리려면 평범한 사용자 행이면서 관리자 권한도 있어야 한다.
        return userRepository.save(
                Users.createAdmin(properties.oauthProvider(), properties.oauthId(), properties.email())
        );
    }

    private void agreeAllTerms(Users user) {
        Set<TermsType> agreed = EnumSet.noneOf(TermsType.class);
        termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(user.getId())
                .forEach(agreement -> agreed.add(agreement.getType()));

        for (TermsType type : TermsType.values()) {
            if (agreed.contains(type)) {
                continue;
            }
            termsAgreementRepository.save(TermsAgreements.create(user, type, true, currentTermsVersion));
        }
    }
}
