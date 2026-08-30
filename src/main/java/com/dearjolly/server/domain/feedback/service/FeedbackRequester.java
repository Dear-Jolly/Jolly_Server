package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;

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
            log.warn("피드백 대상 편지를 찾을 수 없다. letterId={}", letterId);
            return;
        }

        // 기본 우표("준비 중")는 등록 시점에만 붙는 것이라 LLM 이 고를 수 있는 후보가 아니다.
        List<String> stampNames = stampRepository.findAllByNameNot(DEFAULT_STAMP_NAME).stream()
                .map(Stamps::getName)
                .toList();
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
        log.info("피드백 생성 완료. letterId={}, model={}", letterId, generated.model());
    }

    private Optional<Stamps> findStamp(String stampName) {
        if (stampName == null || DEFAULT_STAMP_NAME.equals(stampName)) {
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
