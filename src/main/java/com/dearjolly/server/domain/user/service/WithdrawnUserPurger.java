package com.dearjolly.server.domain.user.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 유예기간이 지난 탈퇴 계정과 그 하위 데이터를 한 번에 지운다.
 * <p>
 * 엔티티 cascade 에 맡기면 계정마다 편지 · 약관 컬렉션을 읽어 한 행씩 지우므로,
 * 쿼리가 계정 수 × 편지 수만큼 늘어난다. 자식부터 부모 순서로 벌크 삭제한다.
 */
@Service
@Transactional(readOnly = true)
public class WithdrawnUserPurger {
    // 외래키를 참조하는 쪽부터 지운다. 순서를 바꾸면 제약조건에 걸린다.
    private static final List<String> DELETE_QUERIES = List.of(
            """
            DELETE FROM FeedbackTips t
            WHERE t.feedback.id IN (
                SELECT f.id FROM Feedbacks f
                WHERE f.letter.id IN (SELECT l.id FROM Letters l WHERE l.user.id IN :userIds))
            """,
            """
            DELETE FROM CorrectionSegments s
            WHERE s.feedback.id IN (
                SELECT f.id FROM Feedbacks f
                WHERE f.letter.id IN (SELECT l.id FROM Letters l WHERE l.user.id IN :userIds))
            """,
            """
            DELETE FROM Feedbacks f
            WHERE f.letter.id IN (SELECT l.id FROM Letters l WHERE l.user.id IN :userIds)
            """,
            "DELETE FROM Letters l WHERE l.user.id IN :userIds",
            "DELETE FROM TermsAgreements a WHERE a.user.id IN :userIds",
            "DELETE FROM Users u WHERE u.id IN :userIds"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void purge(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        for (String query : DELETE_QUERIES) {
            entityManager.createQuery(query)
                    .setParameter("userIds", userIds)
                    .executeUpdate();
        }
        // 벌크 삭제는 영속성 컨텍스트를 거치지 않는다. 이미 올라와 있는 엔티티를 비워
        // 지워진 행을 살아 있는 것으로 착각하지 않게 한다.
        entityManager.clear();
    }
}
