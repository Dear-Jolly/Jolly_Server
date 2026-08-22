package com.dearjolly.server.domain.user.repository;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsAgreementRepository extends JpaRepository<TermsAgreements, Long> {
    List<TermsAgreements> findAllByUserIdOrderByAgreedAtDesc(Long userId);
}
