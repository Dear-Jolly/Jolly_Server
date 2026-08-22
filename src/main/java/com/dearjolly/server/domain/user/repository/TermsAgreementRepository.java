package com.dearjolly.server.domain.user.repository;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreements, Long> {

    /**
     * 한 유저의 동의 이력 전체를 최신순으로 가져온다. 항목이 3개뿐이라
     * 애플리케이션에서 type 별 최신 행을 고르는 편이 윈도우 함수보다 단순하다 (ERD §3.4).
     */
    List<TermsAgreements> findAllByUserIdOrderByAgreedAtDesc(Long userId);
}
