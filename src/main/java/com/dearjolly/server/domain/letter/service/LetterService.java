package com.dearjolly.server.domain.letter.service;

import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.CONTENT_MAX_LENGTH;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.DUPLICATE_WINDOW_SECONDS;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.KOREAN_PATTERN;
import static com.dearjolly.server.domain.letter.constants.LetterValidationConstants.WRITTEN_AT_TOLERANCE_HOURS;

import com.dearjolly.server.domain.feedback.service.FeedbackRequester;
import com.dearjolly.server.domain.letter.dto.request.LetterCreateRequest;
import com.dearjolly.server.domain.letter.dto.response.LetterCreateResult;
import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.repository.LetterRepository;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LetterService {
    private static final Duration WRITTEN_AT_TOLERANCE = Duration.ofHours(WRITTEN_AT_TOLERANCE_HOURS);

    private final LetterRepository letterRepository;
    private final UserRepository userRepository;
    private final FeedbackRequester feedbackRequester;

    @Transactional
    public LetterCreateResult createLetter(Long userId, LetterCreateRequest request) {
        String content = request.content();
        validateContent(content);
        ZoneId timeZone = parseTimeZone(request.timeZone());
        LocalDateTime writtenAt = request.writtenAt();
        validateWrittenAt(writtenAt, timeZone);

        Optional<Letters> duplicated = findRecentDuplicate(userId, content);
        if (duplicated.isPresent()) {
            return LetterCreateResult.duplicated(duplicated.get());
        }

        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Letters letter = letterRepository.save(
                Letters.create(user, content, writtenAt.toLocalDate(), timeZone)
        );

        feedbackRequester.requestFeedback(letter.getId());
        return LetterCreateResult.created(letter);
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
