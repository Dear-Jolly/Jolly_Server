package com.dearjolly.server.domain.feedback.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FeedbackRequester {
    // TODO: AFTER_COMMIT 이벤트 발행 + @Async 워커로 LLM 피드백 파이프라인 연동 (기능명세 3.5)
    public void requestFeedback(Long letterId) {
        log.info("피드백 요청 접수 (임시 구현): letterId={}", letterId);
    }
}
