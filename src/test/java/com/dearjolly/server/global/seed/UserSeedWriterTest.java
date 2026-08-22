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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserSeedWriterTest {
    private static final String OAUTH_ID = "seed-admin";
    private static final String NICKNAME = "jolly";
    private static final String STAMP_NAME = "커피잔";

    private static final LetterSeed COMPLETED_SEED = LetterSeed.completed(
            1,
            "I go running along the river.",
            "I went running along the river.",
            List.of("과거형으로 맞춰 주세요!"),
            STAMP_NAME,
            true
    );

    private static final LetterSeed PENDING_SEED = LetterSeed.pending(
            0, "I almost miss the train this morning."
    );

    private static final List<LetterSeed> SEEDS = List.of(COMPLETED_SEED, PENDING_SEED);

    @Mock
    private UserRepository userRepository;

    @Mock
    private TermsAgreementRepository termsAgreementRepository;

    @Mock
    private LetterRepository letterRepository;

    @Mock
    private StampRepository stampRepository;

    private UserSeedWriter userSeedWriter;

    @BeforeEach
    void setUp() {
        userSeedWriter = new UserSeedWriter(
                userRepository, termsAgreementRepository, letterRepository, stampRepository, new CorrectionDiffer()
        );
        ReflectionTestUtils.setField(userSeedWriter, "currentTermsVersion", "1.0.0");

        given(letterRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(termsAgreementRepository.findAllByUserIdOrderByAgreedAtDesc(anyLong())).willReturn(List.of());
        given(stampRepository.findByName(anyString())).willReturn(Optional.of(stamp()));
    }

    @DisplayName("시드 User 가 없으면 계정·약관 동의·편지를 한 번에 만든다.")
    @Test
    void write() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        Users saved = persistedUser();
        given(userRepository.save(any())).willReturn(saved);

        // when
        UserSeedResult result = userSeedWriter.write(properties(), SEEDS);

        // then
        assertThat(result.userId()).isEqualTo(1L);
        assertThat(result.createdLetters()).isEqualTo(2);
        assertThat(result.completedLetters()).isEqualTo(1);
        assertThat(saved.getNickname()).isEqualTo(NICKNAME);
        verify(termsAgreementRepository, times(TermsType.values().length)).save(any());
    }

    @DisplayName("시드 User 는 관리자 권한으로 만든다. 관리자 로그인이 이 계정으로 붙는다.")
    @Test
    void writeCreatesAdminUser() {
        // given
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.empty());
        given(userRepository.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        // when
        userSeedWriter.write(properties(), List.of());

        // then
        ArgumentCaptor<Users> captor = ArgumentCaptor.forClass(Users.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    @DisplayName("같은 본문의 편지가 이미 있으면 다시 만들지 않는다.")
    @Test
    void writeIsIdempotent() {
        // given
        Users existing = persistedUser();
        existing.updateNickname(NICKNAME);
        given(userRepository.findByOauthProviderAndOauthId(KAKAO, OAUTH_ID)).willReturn(Optional.of(existing));
        userSeedWriter.write(properties(), SEEDS);

        // when
        UserSeedResult result = userSeedWriter.write(properties(), SEEDS);

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
        UserSeedResult result = userSeedWriter.write(properties(), SEEDS);

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
        userSeedWriter.write(properties(), List.of(COMPLETED_SEED));

        // then
        Letters letter = saved.getLetters().getFirst();
        assertThat(letter.getStatus()).isEqualTo(FEEDBACK_COMPLETED);
        assertThat(letter.isRead()).isTrue();
        assertThat(letter.getStamp().getName()).isEqualTo(STAMP_NAME);
        assertThat(letter.getFeedback().getCorrectedContent()).isEqualTo(COMPLETED_SEED.correctedContent());
        assertThat(letter.getFeedback().getTips()).hasSize(COMPLETED_SEED.tips().size());
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
        UserSeedResult result = userSeedWriter.write(properties(), SEEDS);

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
        userSeedWriter.write(properties(), List.of());

        // then
        verify(termsAgreementRepository, times(TermsType.values().length - 1)).save(any());
        verify(letterRepository, never()).save(any());
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

    private Stamps stamp() {
        return Stamps.create(STAMP_NAME, "stamp/커피잔.png");
    }
}
