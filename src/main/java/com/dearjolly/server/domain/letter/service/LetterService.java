package com.dearjolly.server.domain.letter.service;

import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.CONTENT_MAX_LENGTH;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.DUPLICATE_WINDOW_SECONDS;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.KOREAN_PATTERN;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.WRITTEN_AT_TOLERANCE_HOURS;
import static com.dearjolly.server.domain.letter.constants.StampConstants.DEFAULT_STAMP_NAME;

import com.dearjolly.server.domain.letter.dto.request.LetterCreateRequest;
import com.dearjolly.server.domain.letter.dto.response.HomeGetResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResult;
import com.dearjolly.server.domain.letter.dto.response.LetterGetResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterListResponse;
import com.dearjolly.server.domain.letter.dto.response.LetterSummaryResponse;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.entity.Stamps;
import com.dearjolly.server.domain.letter.enums.LetterSort;
import com.dearjolly.server.domain.letter.enums.Status;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.letter.repository.StampRepository;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.storage.FileUrlProvider;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterService {
    private static final Duration WRITTEN_AT_TOLERANCE = Duration.ofHours(WRITTEN_AT_TOLERANCE_HOURS);

    private final LetterRepository letterRepository;
    private final StampRepository stampRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final FileUrlProvider fileUrlProvider;

    @Transactional
    public LetterCreateResult createLetter(Long userId, LetterCreateRequest request) {
        String content = request.content();
        validateContent(content);
        ZoneId timeZone = parseTimeZone(request.timeZone());
        LocalDateTime writtenAt = request.writtenAt();
        validateWrittenAt(writtenAt, timeZone);

        Optional<Letters> duplicated = findRecentDuplicate(userId, content);
        if (duplicated.isPresent()) {
            Letters letter = duplicated.get();
            log.info(
                    "letter_submission_deduplicated userId={} letterId={} contentLength={} status={}",
                    userId, letter.getId(), content.codePointCount(0, content.length()), letter.getStatus()
            );
            return LetterCreateResult.duplicated(letter);
        }

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Letters letter = letterRepository.save(
                Letters.create(user, content, writtenAt.toLocalDate(), timeZone, defaultStamp())
        );

        eventPublisher.publishEvent(new LetterCreatedEvent(letter.getId()));
        log.info(
                "letter_created userId={} letterId={} letterDate={} timeZone={} contentLength={} status={}",
                userId, letter.getId(), letter.getLetterDate(), timeZone.getId(),
                content.codePointCount(0, content.length()), letter.getStatus()
        );
        return LetterCreateResult.created(letter);
    }

    @Transactional
    public LetterGetResponse getLetter(Long userId, Long letterId) {
        Letters letter = letterRepository.findByIdAndUserId(letterId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LETTER_NOT_FOUND, "letterId=" + letterId));

        boolean markedAsRead = letter.isFeedbackCompleted() && !letter.isRead();
        if (letter.isFeedbackCompleted()) {
            letter.markAsRead();
        }
        log.info(
                "letter_detail_viewed userId={} letterId={} status={} responseStatus={} retryCount={} markedAsRead={}",
                userId, letterId, letter.getStatus(), letter.toResponseStatus(), letter.getRetryCount(), markedAsRead
        );
        return LetterGetResponse.of(letter, stampImageOf(letter));
    }

    public LetterListResponse getLetters(Long userId, int page, int size, LetterSort sort) {
        Slice<Letters> letters = letterRepository.findAllByUserId(
                userId, PageRequest.of(page, size, sort.toSort())
        );
        List<LetterSummaryResponse> summaries = letters.getContent().stream()
                .map(letter -> LetterSummaryResponse.of(letter, stampImageOf(letter)))
                .toList();

        log.info(
                "letter_list_viewed userId={} page={} size={} sort={} resultCount={} hasNext={}",
                userId, page, size, sort, summaries.size(), letters.hasNext()
        );
        return LetterListResponse.of(summaries, letters.hasNext());
    }

    public HomeGetResponse getHome(Long userId) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        long stampCount = countStamps(userId);
        log.info("home_viewed userId={} stampCount={}", userId, stampCount);
        return HomeGetResponse.of(user.getNickname(), stampCount);
    }

    private long countStamps(Long userId) {
        return letterRepository.countByUserIdAndStatus(userId, Status.FEEDBACK_COMPLETED);
    }

    private String stampImageOf(Letters letter) {
        Stamps stamp = letter.getStamp();
        return stamp == null ? null : fileUrlProvider.toPublicUrl(stamp.getImageKey());
    }

    // 편지에는 등록 시점부터 "준비 중" 우표가 붙는다. 시드가 돌지 않은 환경이면 우표 없이 등록한다.
    private Stamps defaultStamp() {
        return stampRepository.findByName(DEFAULT_STAMP_NAME)
                .orElseGet(() -> {
                    log.warn("기본 우표({})가 없어 우표 없이 편지를 등록한다. 우표 시드를 확인한다.", DEFAULT_STAMP_NAME);
                    return null;
                });
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.LETTER_CONTENT_REQUIRED);
        }
        if (content.codePointCount(0, content.length()) > CONTENT_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.LETTER_CONTENT_TOO_LONG);
        }
        if (KOREAN_PATTERN.matcher(content).find()) {
            throw new BusinessException(ErrorCode.LETTER_CONTENT_NOT_ENGLISH);
        }
    }

    private ZoneId parseTimeZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            throw new BusinessException(ErrorCode.LETTER_WRITTEN_AT_INVALID, "timeZone 은 필수입니다.");
        }
        try {
            return ZoneId.of(timeZone);
        } catch (DateTimeException e) {
            throw new BusinessException(ErrorCode.LETTER_WRITTEN_AT_INVALID, "timeZone=" + timeZone);
        }
    }

    private void validateWrittenAt(LocalDateTime writtenAt, ZoneId timeZone) {
        if (writtenAt == null) {
            throw new BusinessException(ErrorCode.LETTER_WRITTEN_AT_INVALID, "writtenAt 은 필수입니다.");
        }
        Instant writtenInstant = writtenAt.atZone(timeZone).toInstant();
        Duration gap = Duration.between(writtenInstant, Instant.now()).abs();
        if (gap.compareTo(WRITTEN_AT_TOLERANCE) > 0) {
            throw new BusinessException(ErrorCode.LETTER_WRITTEN_AT_INVALID, "writtenAt=" + writtenAt);
        }
    }

    private Optional<Letters> findRecentDuplicate(Long userId, String content) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(DUPLICATE_WINDOW_SECONDS);
        return letterRepository.findFirstByUserIdOrderByIdDesc(userId)
                .filter(latest -> latest.getContent().equals(content))
                .filter(latest -> latest.getCreatedAt().isAfter(threshold));
    }
}
