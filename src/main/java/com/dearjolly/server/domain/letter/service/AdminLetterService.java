package com.dearjolly.server.domain.letter.service;

import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_COMPLETED;
import static com.dearjolly.server.domain.letter.enums.Status.FEEDBACK_IN_PROGRESS;

import com.dearjolly.server.domain.feedback.service.FeedbackRetryPolicy;
import com.dearjolly.server.domain.letter.dto.response.AdminFailedLetterListResponse;
import com.dearjolly.server.domain.letter.dto.response.AdminFailedLetterResponse;
import com.dearjolly.server.domain.letter.dto.response.AdminLetterRetryResponse;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminLetterService {
    private final LetterRepository letterRepository;
    private final StampRepository stampRepository;
    private final FeedbackRetryPolicy retryPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public AdminFailedLetterListResponse getFailedLetters(Long adminId, int page, int size) {
        Slice<Letters> letters = letterRepository.findFailedLetters(
                FEEDBACK_COMPLETED, PageRequest.of(page, size)
        );
        List<AdminFailedLetterResponse> failedLetters = letters.getContent().stream()
                .map(AdminFailedLetterResponse::from)
                .toList();

        log.info(
                "admin_failed_letters_viewed adminId={} page={} size={} resultCount={} hasNext={}",
                adminId, page, size, failedLetters.size(), letters.hasNext()
        );
        return AdminFailedLetterListResponse.of(failedLetters, letters.hasNext());
    }

    @Transactional
    public AdminLetterRetryResponse retryFeedback(Long adminId, Long letterId) {
        Letters letter = letterRepository.findByIdForFeedback(letterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LETTER_NOT_FOUND, "letterId=" + letterId));
        validateRetryable(letter);

        int usedRetryCount = letter.getRetryCount();
        letter.resetForReprocess(defaultStamp());
        eventPublisher.publishEvent(new LetterCreatedEvent(letterId));

        log.info(
                "admin_feedback_retry_requested adminId={} userId={} letterId={} usedRetryCount={} nextRetryAt={}",
                adminId, letter.getUser().getId(), letterId, usedRetryCount, letter.getNextRetryAt()
        );
        return AdminLetterRetryResponse.from(letter);
    }

    // 처리 중인 편지를 되돌리면 진행 중인 워커와 새 워커가 같은 편지에 동시에 OpenAI 를 호출한다.
    // 멈춤 판정 기준을 넘긴 편지만 워커가 죽은 것으로 보고 되살린다.
    private void validateRetryable(Letters letter) {
        if (letter.getStatus() == FEEDBACK_COMPLETED) {
            throw new BusinessException(ErrorCode.LETTER_FEEDBACK_ALREADY_COMPLETED, "letterId=" + letter.getId());
        }
        LocalDateTime stalledThreshold = LocalDateTime.now().minus(retryPolicy.stalledThreshold());
        if (letter.getStatus() == FEEDBACK_IN_PROGRESS && !letter.getUpdatedAt().isBefore(stalledThreshold)) {
            throw new BusinessException(ErrorCode.LETTER_FEEDBACK_IN_PROGRESS, "letterId=" + letter.getId());
        }
    }

    private Stamps defaultStamp() {
        return stampRepository.findByName(DEFAULT_STAMP_NAME)
                .orElseGet(() -> {
                    log.warn("기본 우표({})가 없어 우표 없이 재처리한다. 우표 시드를 확인한다.", DEFAULT_STAMP_NAME);
                    return null;
                });
    }
}
