package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_COMPLETED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FeedbackStateService {
    private final LetterRepository letterRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(Long letterId) {
        return letterRepository.startFeedback(
                letterId, SUBMITTED, FEEDBACK_IN_PROGRESS, LocalDateTime.now()
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackFailureResult handleFailure(Long letterId, boolean retryable) {
        Letters letter = letterRepository.findById(letterId).orElse(null);
        if (letter == null || letter.getStatus() != FEEDBACK_IN_PROGRESS) {
            return FeedbackFailureResult.failed(0);
        }
        if (!retryable) {
            letter.failFeedback();
            return FeedbackFailureResult.failed(letter.getRetryCount());
        }
        if (letter.isRetryExhausted()) {
            if (letter.isRecoveryExhausted()) {
                letter.failFeedback();
                return FeedbackFailureResult.failed(letter.getRetryCount());
            }
            letter.awaitRecovery();
            return FeedbackFailureResult.awaitingRecovery(letter.getRetryCount());
        }
        letter.retryFeedback();
        return FeedbackFailureResult.retry(letter.getRetryCount());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long letterId) {
        letterRepository.findById(letterId)
                .filter(letter -> letter.getStatus() != FEEDBACK_COMPLETED)
                .ifPresent(Letters::failFeedback);
    }
}
