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

@Slf4j
@Service
@RequiredArgsConstructor
public class WithdrawnUserCleanupScheduler {
    private final UserRepository userRepository;

    @Value("${dearjolly.withdrawal.retention-days}")
    private int retentionDays;

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
