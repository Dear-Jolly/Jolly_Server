package com.dearjolly.server.global.admin.service;

import static com.dearjolly.server.domain.user.enums.Role.ROLE_ADMIN;
import static com.dearjolly.server.domain.user.enums.UserStatus.ACTIVE;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.admin.AdminProperties;
import com.dearjolly.server.global.admin.dto.request.AdminLoginRequest;
import com.dearjolly.server.global.admin.dto.response.AdminLoginResponse;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthService {
    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    // 관리자는 회원가입을 하지 않는다. 시드가 만들어 둔 계정에 로그인할 뿐이라 계정이 없으면 로그인도 실패한다.
    // 발급되는 토큰은 소셜 로그인이 주는 것과 같고, 다른 점은 그 계정의 role 뿐이다.
    @Transactional
    public AdminLoginResponse login(AdminLoginRequest request) {
        if (!matches(request)) {
            throw new BusinessException(ErrorCode.ADMIN_CREDENTIALS_INVALID);
        }
        Users admin = userRepository.findFirstByRoleAndStatus(ROLE_ADMIN, ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_CREDENTIALS_INVALID, "관리자 계정 없음"));

        String accessToken = jwtProvider.createAccessToken(admin.getId(), admin.getRole());
        String refreshToken = jwtProvider.createRefreshToken(admin.getId(), admin.getRole());
        admin.updateRefreshToken(refreshToken);

        return AdminLoginResponse.of(accessToken, refreshToken);
    }

    // 아이디가 틀렸을 때 비밀번호 비교를 건너뛰면 응답 시간이 아이디 존재 여부를 흘린다. 둘 다 상수 시간으로 비교한다.
    private boolean matches(AdminLoginRequest request) {
        boolean sameUsername = constantTimeEquals(adminProperties.username(), request.username());
        boolean samePassword = constantTimeEquals(adminProperties.password(), request.password());
        return sameUsername & samePassword;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
