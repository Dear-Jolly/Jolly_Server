package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.FAILED_STAMP_NAME;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackStateService {
    private final LetterRepository letterRepository;
    private final FeedbackRetryPolicy retryPolicy;
    private final StampRepository stampRepository;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FeedbackLogContext getLogContext(Long letterId) {
        return letterRepository.findById(letterId)
                .map(FeedbackLogContext::from)
                .orElseGet(() -> FeedbackLogContext.unknown(letterId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean start(Long letterId) {
        LocalDateTime now = LocalDateTime.now();
        return letterRepository.startFeedback(
                letterId, SUBMITTED, FEEDBACK_IN_PROGRESS, now
        ) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackFailureResult handleFailure(Long letterId) {
        Letters letter = letterRepository.findByIdForFeedback(letterId).orElse(null);
        if (letter == null || letter.getStatus() != FEEDBACK_IN_PROGRESS) {
            return FeedbackFailureResult.ignored(letter == null ? 0 : letter.getRetryCount());
        }
        return scheduleRetryOrFail(letter, LocalDateTime.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FeedbackFailureResult recoverStalled(Long letterId, LocalDateTime threshold, LocalDateTime now) {
        Letters letter = letterRepository.findByIdForFeedback(letterId).orElse(null);
        if (letter == null || letter.getStatus() != FEEDBACK_IN_PROGRESS
                || !letter.getUpdatedAt().isBefore(threshold)) {
            return FeedbackFailureResult.ignored(letter == null ? 0 : letter.getRetryCount());
        }
        return scheduleRetryOrFail(letter, now);
    }

    private FeedbackFailureResult scheduleRetryOrFail(Letters letter, LocalDateTime now) {
        int retryCount = letter.getRetryCount();
        if (retryPolicy.isExhausted(retryCount)) {
            letter.failFeedback();
            return FeedbackFailureResult.failed(retryCount);
        }
        Duration delay = retryPolicy.nextDelay(retryCount);
        LocalDateTime nextRetryAt = now.plus(delay);
        letter.scheduleRetry(nextRetryAt, failedStamp());
        return FeedbackFailureResult.retryScheduled(
                letter.getRetryCount(), nextRetryAt, delay.toSeconds()
        );
    }

    // 우표 하나 때문에 재시도 예약까지 잃으면 편지가 준비 중에 갇힌다. 없으면 우표만 포기한다.
    private Stamps failedStamp() {
        return stampRepository.findByName(FAILED_STAMP_NAME)
                .orElseGet(() -> {
                    log.warn("실패 우표({})가 없어 우표를 바꾸지 않고 재시도만 예약한다. 우표 시드를 확인한다.", FAILED_STAMP_NAME);
                    return null;
                });
    }
}
