package com.dearjolly.server.domain.feedback.service;

import com.dearjolly.server.domain.letter.service.LetterCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class FeedbackEventListener {
    private final FeedbackWorker feedbackWorker;

    @Async("feedbackExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void requestFeedback(LetterCreatedEvent event) {
        feedbackWorker.process(event.letterId());
    }
}
