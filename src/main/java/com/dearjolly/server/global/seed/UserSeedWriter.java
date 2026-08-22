package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;

import com.dearjolly.server.domain.feedback.entity.CorrectionSegments;
import com.dearjolly.server.domain.feedback.entity.FeedbackTips;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.feedback.service.CorrectionDiffer;
import com.dearjolly.server.domain.feedback.service.CorrectionPair;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSeedWriter {
    private static final String MODEL = "seed-mock";

    private static final ZoneId SEED_ZONE = ZoneId.of("Asia/Seoul");

    private final UserRepository userRepository;
    private final TermsAgreementRepository termsAgreementRepository;
    private final LetterRepository letterRepository;
    private final StampRepository stampRepository;
    private final CorrectionDiffer correctionDiffer;

    @Value("${dearjolly.terms.current-version}")
    private String currentTermsVersion;

    @Transactional
    public UserSeedResult write(UserSeedProperties properties, List<LetterSeed> letterSeeds) {
        Users user = findOrCreateUser(properties);
        agreeAllTerms(user);

        Set<String> written = user.getLetters().stream()
                .map(Letters::getContent)
                .collect(Collectors.toCollection(HashSet::new));
        int created = 0;
        int completed = 0;
        for (LetterSeed seed : oldestFirst(letterSeeds)) {
            if (!written.add(seed.content())) {
                continue;
            }
            created++;
            if (writeLetter(user, seed)) {
                completed++;
            }
        }
        return new UserSeedResult(user.getId(), created, completed);
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

    private List<LetterSeed> oldestFirst(List<LetterSeed> letterSeeds) {
        return letterSeeds.stream()
                .sorted(Comparator.comparingInt(LetterSeed::daysAgo).reversed())
                .toList();
    }

    private boolean writeLetter(Users user, LetterSeed seed) {
        LocalDate letterDate = LocalDate.now(SEED_ZONE).minusDays(seed.daysAgo());
        Letters letter = letterRepository.save(
                Letters.create(user, seed.content(), letterDate, SEED_ZONE, defaultStamp())
        );
        if (!seed.isFeedbackCompleted()) {
            return false;
        }
        return completeFeedback(letter, seed);
    }

    // 편지 등록 API 와 같은 모양으로 "준비 중" 우표를 붙인다. 피드백까지 있는 편지는 곧바로 덮어쓴다.
    private Stamps defaultStamp() {
        return stampRepository.findByName(DEFAULT_STAMP_NAME).orElse(null);
    }

    private boolean completeFeedback(Letters letter, LetterSeed seed) {
        Optional<Stamps> stamp = stampRepository.findByName(seed.stampName());
        if (stamp.isEmpty()) {
            log.warn("우표({})가 없어 편지를 제출 상태로 남긴다. 우표 시드를 먼저 확인한다.", seed.stampName());
            return false;
        }

        List<CorrectionPair> pairs = correctionDiffer.diff(letter.getContent(), seed.correctedContent());
        Feedbacks feedback = Feedbacks.create(letter, seed.correctedContent(), MODEL);
        int sequence = 1;
        for (CorrectionPair pair : pairs) {
            CorrectionSegments.create(feedback, sequence++, pair.originalText(), pair.correctedText());
        }
        int sortOrder = 1;
        for (String tip : seed.tips()) {
            FeedbackTips.create(feedback, tip, sortOrder++);
        }

        letter.completeFeedback(stamp.get());
        if (seed.read()) {
            letter.markAsRead();
        }
        return true;
    }
}
