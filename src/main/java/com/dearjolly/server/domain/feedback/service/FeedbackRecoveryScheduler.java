package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_FAILED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;

import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.service.LetterCreatedEvent;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackRecoveryScheduler {
    private static final int MAX_RECOVERY_COUNT = 2;

    private final LetterRepository letterRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Scheduled(fixedDelayString = "${dearjolly.feedback.recovery-interval-millis:600000}")
    @Transactional
    public void recoverStalledFeedback() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime submittedThreshold = now.minusHours(1);
        LocalDateTime inProgressThreshold = now.minusMinutes(15);
        List<Long> submitted = letterRepository.findIdsByStatusAndUpdatedAtBefore(SUBMITTED, submittedThreshold);
        List<Long> inProgress = letterRepository.findIdsByStatusAndUpdatedAtBefore(
                FEEDBACK_IN_PROGRESS, inProgressThreshold
        );

        long failedCount = submitted.stream()
                .filter(letterId -> recoverAndCheckFailed(letterId, SUBMITTED, submittedThreshold, now))
                .count();
        failedCount += inProgress.stream()
                .filter(letterId -> recoverAndCheckFailed(letterId, FEEDBACK_IN_PROGRESS, inProgressThreshold, now))
                .count();

        if (!submitted.isEmpty() || !inProgress.isEmpty()) {
            log.warn("feedback_recovery_scan_completed submittedCount={} inProgressCount={} failedCount={}",
                    submitted.size(), inProgress.size(), failedCount);
        }
    }

    private boolean recoverAndCheckFailed(
            Long letterId, Status expectedStatus, LocalDateTime threshold, LocalDateTime now
    ) {
        int recovered = letterRepository.recoverFeedback(
                letterId, expectedStatus, SUBMITTED, threshold, now, MAX_RECOVERY_COUNT
        );
        if (recovered == 1) {
            FeedbackLogContext context = logContext(letterId);
            log.warn(
                    "feedback_job_recovered userId={} nickname={} letterId={} previousStatus={} retryCount={} "
                            + "recoveryCount={}",
                    context.userId(), sanitize(context.nickname()), letterId, expectedStatus,
                    context.retryCount(), context.recoveryCount()
            );
            eventPublisher.publishEvent(new LetterCreatedEvent(letterId));
            return false;
        }
        int failed = letterRepository.failExhaustedRecovery(
                letterId, expectedStatus, FEEDBACK_FAILED, threshold, now, MAX_RECOVERY_COUNT
        );
        if (failed == 1) {
            FeedbackLogContext context = logContext(letterId);
            log.error(
                    "feedback_recovery_exhausted userId={} nickname={} letterId={} previousStatus={} "
                            + "retryCount={} recoveryCount={} status={}",
                    context.userId(), sanitize(context.nickname()), letterId, expectedStatus,
                    context.retryCount(), context.recoveryCount(), context.status()
            );
            return true;
        }
        return false;
    }

    private FeedbackLogContext logContext(Long letterId) {
        return letterRepository.findById(letterId)
                .map(FeedbackLogContext::from)
                .orElseGet(() -> FeedbackLogContext.unknown(letterId));
    }
}
