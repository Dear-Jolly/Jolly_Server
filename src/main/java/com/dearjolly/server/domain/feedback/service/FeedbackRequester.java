package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;
import static com.dearjolly.server.domain.letter.constants.StampConstants.FAILED_STAMP_NAME;
import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;

import com.dearjolly.server.domain.feedback.entity.CorrectionSegments;
import com.dearjolly.server.domain.feedback.entity.FeedbackTips;
import com.dearjolly.server.domain.feedback.entity.Feedbacks;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackRequester {
    private final LetterRepository letterRepository;
    private final StampRepository stampRepository;
    private final LlmClient llmClient;
    private final CorrectionDiffer correctionDiffer;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void requestFeedback(Long letterId) {
        Letters letter = letterRepository.findByIdForFeedback(letterId).orElse(null);
        if (letter == null) {
            log.warn("feedback_target_missing letterId={}", letterId);
            return;
        }

        // 기본 우표("준비 중")는 등록 시점에만 붙는 것이라 LLM 이 고를 수 있는 후보가 아니다.
        List<String> stampNames = stampRepository.findAllByNameNotIn(
                        List.of(DEFAULT_STAMP_NAME, FAILED_STAMP_NAME)
                ).stream()
                .map(Stamps::getName)
                .toList();
        long startedAt = System.nanoTime();
        log.info(
                "openai_feedback_requested userId={} nickname={} letterId={} contentLength={} stampCandidateCount={}",
                letter.getUser().getId(), sanitize(letter.getUser().getNickname()), letterId,
                letter.getContent().codePointCount(0, letter.getContent().length()), stampNames.size()
        );
        LlmFeedback generated = llmClient.correct(letter.getContent(), stampNames);

        Optional<Stamps> stamp = findStamp(generated.stampName());
        if (stamp.isEmpty()) {
            throw new IllegalStateException("OpenAI가 선택한 우표를 찾을 수 없습니다.");
        }

        List<CorrectionPair> pairs = correctionDiffer.diff(letter.getContent(), generated.correctedContent());
        if (!correctionDiffer.isValid(pairs, letter.getContent(), generated.correctedContent())) {
            throw new IllegalStateException("교정 세그먼트 불변식이 깨졌습니다.");
        }

        saveFeedback(letter, generated, pairs);
        letter.completeFeedback(stamp.get());
        log.info(
                "openai_feedback_completed userId={} nickname={} letterId={} model={} stampName={} tipCount={} "
                        + "segmentCount={} elapsedMs={} status={}",
                letter.getUser().getId(), sanitize(letter.getUser().getNickname()), letterId,
                sanitize(generated.model()), sanitize(generated.stampName()), generated.tips().size(), pairs.size(),
                (System.nanoTime() - startedAt) / 1_000_000, letter.getStatus()
        );
    }

    private Optional<Stamps> findStamp(String stampName) {
        if (stampName == null || DEFAULT_STAMP_NAME.equals(stampName) || FAILED_STAMP_NAME.equals(stampName)) {
            return Optional.empty();
        }
        return stampRepository.findByName(stampName);
    }

    private void saveFeedback(Letters letter, LlmFeedback generated, List<CorrectionPair> pairs) {
        Feedbacks feedback = Feedbacks.create(letter, generated.correctedContent(), generated.model());
        int sequence = 1;
        for (CorrectionPair pair : pairs) {
            CorrectionSegments.create(feedback, sequence++, pair.originalText(), pair.correctedText());
        }
        int sortOrder = 1;
        for (String tip : generated.tips()) {
            FeedbackTips.create(feedback, tip, sortOrder++);
        }
    }
}
