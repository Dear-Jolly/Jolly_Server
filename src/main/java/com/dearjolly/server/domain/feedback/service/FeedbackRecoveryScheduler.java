package com.dearjolly.server.domain.feedback.service;

import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;
import static com.dearjolly.server.domain.letter.enums.Status.SUBMITTED;
import static com.dearjolly.server.global.logging.LogValueSanitizer.sanitize;

import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.service.LetterCreatedEvent;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedbackRecoveryScheduler {
    private static final String SCHEDULED_TRIGGER = "scheduled";
    private static final String STARTUP_TRIGGER = "startup";
    private static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Seoul");

    private final LetterRepository letterRepository;
    private final FeedbackRetryPolicy retryPolicy;
    private final FeedbackStateService feedbackStateService;
    private final FeedbackWorker feedbackWorker;
    private final ApplicationEventPublisher eventPublisher;
    private final TaskScheduler feedbackRetryScheduler;

    @Scheduled(cron = "${dearjolly.feedback.recovery-cron:0 0 0 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void recoverLostFeedback() {
        recover(SCHEDULED_TRIGGER);
    }

    // 재시도 예약은 인메모리라 배포·재기동으로 사라진다. 기동 직후 한 번 훑지 않으면
    // 예약을 잃은 편지가 다음 0시까지 아무에게도 집히지 않는다.
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        recover(STARTUP_TRIGGER);
    }

    private void recover(String trigger) {
        LocalDateTime now = LocalDateTime.now();
        List<Long> dueLetterIds = letterRepository.findDueFeedbackIds(
                SUBMITTED, now
        );
        dueLetterIds.forEach(letterId -> eventPublisher.publishEvent(new LetterCreatedEvent(letterId)));

        LocalDateTime stalledThreshold = now.minus(retryPolicy.stalledThreshold());
        List<Long> stalledLetterIds = letterRepository.findStalledFeedbackIds(
                FEEDBACK_IN_PROGRESS, stalledThreshold
        );
        stalledLetterIds.forEach(letterId -> recoverStalledFeedback(letterId, stalledThreshold, now));

        log.info(
                "feedback_recovery_completed trigger={} dueCount={} stalledCount={}",
                trigger, dueLetterIds.size(), stalledLetterIds.size()
        );
    }

    private void recoverStalledFeedback(Long letterId, LocalDateTime threshold, LocalDateTime now) {
        FeedbackFailureResult result = feedbackStateService.recoverStalled(letterId, threshold, now);
        FeedbackLogContext context = feedbackStateService.getLogContext(letterId);
        if (result.retryScheduled()) {
            scheduleRetry(letterId, result.nextRetryAt());
            log.warn(
                    "feedback_stalled_retry_registered userId={} nickname={} letterId={} retryCount={} "
                            + "nextRetryAt={} delaySeconds={}",
                    context.userId(), sanitize(context.nickname()), letterId, result.retryCount(),
                    result.nextRetryAt(), result.delaySeconds()
            );
        } else if (result.failed()) {
            log.error(
                    "feedback_stalled_failed userId={} nickname={} letterId={} retryCount={} status={}",
                    context.userId(), sanitize(context.nickname()), letterId, result.retryCount(), context.status()
            );
        }
    }

    private void scheduleRetry(Long letterId, LocalDateTime nextRetryAt) {
        Instant executionTime = nextRetryAt.atZone(SERVER_ZONE).toInstant();
        feedbackRetryScheduler.schedule(
                () -> feedbackWorker.process(letterId),
                executionTime
        );
    }
}
