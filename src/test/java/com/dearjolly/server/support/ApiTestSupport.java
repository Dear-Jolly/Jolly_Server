package com.dearjolly.server.support;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;
import static com.dearjolly.server.domain.letter.constants.StampConstants.FAILED_STAMP_NAME;

import com.dearjolly.server.domain.feedback.entity.CorrectionSegments;
import com.dearjolly.server.domain.feedback.entity.FeedbackTips;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.Role;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.version.entity.AppVersions;
import com.dearjolly.server.global.version.enums.Platform;
import com.dearjolly.server.global.version.repository.AppVersionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import io.restassured.RestAssured;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class ApiTestSupport {
    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TermsAgreementRepository termsAgreementRepository;

    @Autowired
    protected LetterRepository letterRepository;

    @Autowired
    protected StampRepository stampRepository;

    @Autowired
    protected AppVersionRepository appVersionRepository;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected JwtProvider jwtProvider;

    @Autowired
    protected EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUpPort() {
        RestAssured.port = port;
    }

    protected Users 유저를_저장한다(String oauthId) {
        return userRepository.save(Users.create(OauthProvider.KAKAO, oauthId, "jolly@example.com"));
    }

    protected Users 온보딩을_마친_유저를_저장한다(String oauthId, String nickname) {
        Users user = 유저를_저장한다(oauthId);
        user.updateNickname(nickname);
        필수약관에_동의한다(user);
        return userRepository.save(user);
    }

    protected void 필수약관에_동의한다(Users user) {
        termsAgreementRepository.save(TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"));
        termsAgreementRepository.save(TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0"));
    }

    protected Stamps 기본우표를_저장한다() {
        return stampRepository.findByName(DEFAULT_STAMP_NAME)
                .orElseGet(() -> stampRepository.save(
                        Stamps.create(DEFAULT_STAMP_NAME, "stamp/" + DEFAULT_STAMP_NAME + ".png")));
    }

    protected Stamps 실패우표를_저장한다() {
        return stampRepository.findByName(FAILED_STAMP_NAME)
                .orElseGet(() -> stampRepository.save(
                        Stamps.create(FAILED_STAMP_NAME, "stamp/" + FAILED_STAMP_NAME + ".png")));
    }

    protected Letters 편지를_저장한다(Users user, String content, LocalDate letterDate) {
        return letterRepository.save(
                Letters.create(user, content, letterDate, ZoneId.of("Asia/Seoul"), 기본우표를_저장한다()));
    }

    protected Letters 피드백완료_편지를_저장한다(Users user, String content, LocalDate letterDate, String stampName) {
        return transactionTemplate.execute(status -> {
            Stamps stamp = Stamps.create(stampName, "stamps/" + stampName + ".png");
            entityManager.persist(stamp);
            Letters letter = Letters.create(user, content, letterDate, ZoneId.of("Asia/Seoul"), null);
            letter.completeFeedback(stamp);
            entityManager.persist(letter);
            return letter;
        });
    }

    protected Letters 첫_실패후_재시도를_기다리는_편지를_저장한다(Users user, String content, LocalDate letterDate) {
        Letters letter = 편지를_저장한다(user, content, letterDate);
        letter.scheduleRetry(LocalDateTime.now().plusSeconds(30), 실패우표를_저장한다());
        return letterRepository.save(letter);
    }

    protected Letters 피드백을_완료한다(Letters letter, String stampName) {
        return transactionTemplate.execute(status -> {
            Stamps stamp = stampRepository.findByName(stampName)
                    .orElseGet(() -> stampRepository.save(Stamps.create(stampName, "stamps/" + stampName + ".png")));
            Letters found = letterRepository.findById(letter.getId()).orElseThrow();
            found.completeFeedback(stamp);
            return found;
        });
    }

    protected Letters 피드백을_붙여_저장한다(
            Letters letter,
            String correctedContent,
            List<String[]> segments,
            List<String> tips
    ) {
        Feedbacks feedback = Feedbacks.create(letter, correctedContent, "claude-test");
        int sequence = 1;
        for (String[] segment : segments) {
            CorrectionSegments.create(feedback, sequence++, segment[0], segment[1]);
        }
        int sortOrder = 1;
        for (String tip : tips) {
            FeedbackTips.create(feedback, tip, sortOrder++);
        }
        return letterRepository.save(letter);
    }

    protected AppVersions 최소지원버전을_저장한다(Platform platform, String minSupportedVersion) {
        return appVersionRepository.save(AppVersions.create(platform, minSupportedVersion));
    }

    protected String 액세스토큰(Users user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId(), user.getRole());
    }

    // 관리자도 평범한 사용자 행이다. role 만 ROLE_ADMIN 이다.
    protected Users 관리자_유저를_저장한다(String oauthId, String nickname) {
        Users user = userRepository.save(Users.createAdmin(OauthProvider.KAKAO, oauthId, "admin@example.com"));
        user.updateNickname(nickname);
        필수약관에_동의한다(user);
        return userRepository.save(user);
    }

    protected long 실행된_쿼리수(Runnable 호출) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        호출.run();
        return statistics.getPrepareStatementCount();
    }

    protected String 관리자_액세스토큰() {
        return 액세스토큰(관리자_유저를_저장한다("admin-oauth-id", "admin"));
    }
}
