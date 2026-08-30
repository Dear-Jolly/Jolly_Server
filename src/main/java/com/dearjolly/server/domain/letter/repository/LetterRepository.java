package com.dearjolly.server.domain.letter.repository;

import com.dearjolly.server.domain.letter.entity.Letters;
import com.dearjolly.server.domain.letter.enums.Status;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LetterRepository extends JpaRepository<Letters, Long> {
    Optional<Letters> findFirstByUserIdOrderByIdDesc(Long userId);

    @Query("""
            SELECT letter.id FROM Letters letter
            WHERE letter.status = :status AND letter.nextRetryAt <= :now
            ORDER BY letter.nextRetryAt, letter.id
            """)
    List<Long> findDueFeedbackIds(
            @Param("status") Status status,
            @Param("now") LocalDateTime now
    );

    @Query("""
            SELECT letter.id FROM Letters letter
            WHERE letter.status = :status AND letter.updatedAt < :threshold
            ORDER BY letter.updatedAt, letter.id
            """)
    List<Long> findStalledFeedbackIds(
            @Param("status") Status status,
            @Param("threshold") LocalDateTime threshold
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT letter FROM Letters letter WHERE letter.id = :letterId")
    Optional<Letters> findByIdForFeedback(@Param("letterId") Long letterId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Letters letter
            SET letter.status = :inProgress,
                letter.nextRetryAt = null,
                letter.updatedAt = :now,
                letter.version = letter.version + 1
            WHERE letter.id = :letterId
              AND letter.status = :submitted
              AND (letter.nextRetryAt IS NULL OR letter.nextRetryAt <= :now)
            """)
    int startFeedback(
            @Param("letterId") Long letterId,
            @Param("submitted") Status submitted,
            @Param("inProgress") Status inProgress,
            @Param("now") LocalDateTime now
    );

    @EntityGraph(attributePaths = {"stamp", "feedback"})
    Optional<Letters> findByIdAndUserId(Long id, Long userId);

    // feedback 을 함께 가져오지 않으면 편지마다 조회가 한 번씩 더 나간다.
    // 비소유 측 @OneToOne 은 프록시를 만들 수 없어, Hibernate 가 null 여부를 알려고 행마다 쿼리를 날린다.
    @EntityGraph(attributePaths = {"stamp", "feedback"})
    Slice<Letters> findAllByUserId(Long userId, Pageable pageable);

    long countByUserIdAndStatus(Long userId, Status status);
}
