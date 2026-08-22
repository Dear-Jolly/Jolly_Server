package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 탈퇴 유예기간(기본 30일)이 지난 계정을 완전 삭제한다.
 *
 * <p>삭제 순서를 코드가 지정하지 않는다. 유저 엔티티 하나를 지우면 약관 이력·편지·피드백·
 * 교정 조각·팁이 JPA cascade 로 함께 사라진다 (ERD §3.3). 그래서 벌크 DELETE 가 아니라
 * 엔티티를 조회한 뒤 delete 해야 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnUserCleanupScheduler {

    private final UserRepository userRepository;

    @Value("${dearjolly.withdrawal.retention-days}")
    private int retentionDays;

    /** 매일 새벽 4시 (기능명세 §3.1.3) */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteExpiredWithdrawnUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        List<Users> expired = userRepository.findAllByDeletedAtBefore(threshold);
        if (expired.isEmpty()) {
            return;
        }
        userRepository.deleteAll(expired);
        log.info("유예기간이 지난 탈퇴 계정 {}건을 삭제했다. threshold={}", expired.size(), threshold);
    }
}
