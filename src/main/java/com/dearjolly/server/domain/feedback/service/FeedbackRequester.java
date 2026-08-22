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
import org.springframework.transaction.annotation.Transactional;

// TODO: 편지 트랜잭션 커밋 후 비동기로 옮긴다. 지금은 mock 이 즉시 끝나 문제가 없지만,
//       실제 LLM 을 붙이면 편지 작성 응답이 LLM 응답만큼 늦어지고 롤백돼도 피드백이 나간다.
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackRequester {
    private final LetterRepository letterRepository;
    private final StampRepository stampRepository;
    private final LlmClient llmClient;
    private final CorrectionDiffer correctionDiffer;

    @Transactional
    public void requestFeedback(Long letterId) {
        Letters letter = letterRepository.findById(letterId).orElse(null);
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
            // 우표 후보가 없으면 완료 상태로 만들 수 없다. SUBMITTED 로 남겨 두고 재처리에 맡긴다.
            log.warn("우표를 고르지 못해 피드백을 완료하지 않는다. letterId={}", letterId);
            return;
        }

        List<CorrectionPair> pairs = correctionDiffer.diff(letter.getContent(), generated.correctedContent());
        if (!correctionDiffer.isValid(pairs, letter.getContent(), generated.correctedContent())) {
            log.error("교정 세그먼트 불변식이 깨져 피드백을 저장하지 않는다. letterId={}", letterId);
            return;
        }

        saveFeedback(letter, generated, pairs);
        letter.completeFeedback(stamp.get());
        log.info("피드백 생성 완료 (mock). letterId={}, model={}", letterId, generated.model());
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
