package com.dearjolly.server.global.seed;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_COMPLETED;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static com.dearjolly.server.domain.user.enums.OauthProvider.KAKAO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dearjolly.server.domain.feedback.service.CorrectionDiffer;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MockUserSeedWriterTest {
    private static final String OAUTH_ID = "mock-user";
    private static final String NICKNAME = "jolly";
    private static final String STAMP_NAME = "커피잔";

    private static final MockLetterSeed COMPLETED_SEED = MockLetterSeed.completed(
            1,
            "I go running along the river.",
            "I went running along the river.",
            List.of("과거형으로 맞춰 주세요!"),
            STAMP_NAME,
            true
    );

    private static final MockLetterSeed PENDING_SEED = MockLetterSeed.pending(
            0, "I almost miss the train this morning."
    );

    private static final List<MockLetterSeed> SEEDS = List.of(COMPLETED_SEED, PENDING_SEED);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private StampRepository stampRepository;

    private MockUserSeedWriter mockUserSeedWriter;

    @BeforeEach
    void setUp() {
        mockUserSeedWriter = new MockUserSeedWriter(
                userRepository, termsAgreementRepository, letterRepository, stampRepository, new CorrectionDiffer()
        );
        ReflectionTestUtils.setField(mockUserSeedWriter, "currentTermsVersion", "1.0.0");

        given(letterRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(anyLong())).willReturn(List.of());
        given(stampRepository.findByName(anyString())).willReturn(Optional.of(stamp()));
    }

    @DisplayName("목 사용자가 없으면 계정·약관 동의·편지를 한 번에 만든다.")
    @Test
    void write() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        Users saved = persistedUser();
        given(userRepository.save(any())).willReturn(saved);

        // when
        MockUserSeedResult result = mockUserSeedWriter.write(properties(), SEEDS);

        // then
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.createdLetters()).isEqualTo(2);
        assertThat(result.completedLetters()).isEqualTo(1);
        assertThat(saved.getNickname()).isEqualTo(NICKNAME);
        verify(termsAgreementRepository, times(TermsType.values().length)).save(any());
    }

    @DisplayName("같은 본문의 편지가 이미 있으면 다시 만들지 않는다.")
    @Test
    void writeIsIdempotent() {
        // given
        Users existing = persistedUser();
        existing.updateNickname(NICKNAME);
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.of(existing));
        mockUserSeedWriter.write(properties(), SEEDS);

        // when
        MockUserSeedResult result = mockUserSeedWriter.write(properties(), SEEDS);

        // then
        assertThat(result.createdLetters()).isZero();
        assertThat(existing.getLetters()).hasSize(SEEDS.size());
        verify(letterRepository, times(SEEDS.size())).save(any());
    }

    @DisplayName("편지 본문에 맞는 우표가 없으면 피드백을 붙이지 않고 제출 상태로 남긴다.")
    @Test
    void writeWithoutStamp() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        Users saved = persistedUser();
        given(userRepository.save(any())).willReturn(saved);
        given(stampRepository.findByName(anyString())).willReturn(Optional.empty());

        // when
        MockUserSeedResult result = mockUserSeedWriter.write(properties(), SEEDS);

        // then
        assertThat(result.completedLetters()).isZero();
        assertThat(saved.getLetters()).extracting(Letters::getStatus).containsOnly(SUBMITTED);
    }

    @DisplayName("피드백이 있는 편지는 완료 상태·읽음 처리까지 끝난 채로 들어간다.")
    @Test
    void writeCompletedLetter() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        Users saved = persistedUser();
        given(userRepository.save(any())).willReturn(saved);

        // when
        mockUserSeedWriter.write(properties(), List.of(COMPLETED_SEED));

        // then
        Letters letter = saved.getLetters().getFirst();
        assertThat(letter.getStatus()).isEqualTo(FEEDBACK_COMPLETED);
        assertThat(letter.isRead()).isTrue();
        assertThat(letter.getStamp().getName()).isEqualTo(STAMP_NAME);
        assertThat(letter.getFeedback().getCorrectedContent()).isEqualTo(COMPLETED_SEED.correctedContent());
        assertThat(letter.getFeedback().getTips()).hasSize(COMPLETED_SEED.tips().size());
    }

    @DisplayName("탈퇴한 목 사용자가 남아 있으면 소셜 식별자를 풀고 계정을 다시 만든다.")
    @Test
    void writeAfterWithdrawal() {
        // given
        Users withdrawn = persistedUser();
        withdrawn.withdraw();
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.of(withdrawn));
        Users rejoined = persistedUser();
        given(userRepository.save(any())).willReturn(rejoined);

        // when
        MockUserSeedResult result = mockUserSeedWriter.write(properties(), SEEDS);

        // then
        verify(userRepository).saveAndFlush(withdrawn);
        assertThat(withdrawn.getOauthId()).startsWith(OAUTH_ID).isNotEqualTo(OAUTH_ID);
        assertThat(result.createdLetters()).isEqualTo(SEEDS.size());
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
        mockUserSeedWriter.write(properties(), List.of());

        // then
        verify(termsAgreementRepository, times(TermsType.values().length - 1)).save(any());
        verify(letterRepository, never()).save(any());
    }

    private MockUserSeedProperties properties() {
        return new MockUserSeedProperties(true, KAKAO, OAUTH_ID, "mock@dearjolly.local", NICKNAME);
    }

    private Users persistedUser() {
        Users user = Users.create(KAKAO, OAUTH_ID, "mock@dearjolly.local");
        ReflectionTestUtils.setField(user, "id", 1L);
        return user;
    }

    private TermsAgreements agreement(Users user, TermsType type) {
        return TermsAgreements.create(user, type, true, "1.0.0");
    }

    private Stamps stamp() {
        return Stamps.create(STAMP_NAME, "stamp/커피잔.png");
    }
}
