package com.dearjolly.server.domain.user.service;

import com.dearjolly.server.domain.user.dto.request.LoginRequest;
import com.dearjolly.server.domain.user.dto.request.NicknameUpdateRequest;
import com.dearjolly.server.domain.user.dto.response.LoginResponse;
import com.dearjolly.server.domain.user.dto.response.NicknameUpdateResponse;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.exception.exception.BusinessException;
import com.dearjolly.server.global.exception.response.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Users user = userRepository.findByEmail(request.email())
                .orElseGet(() -> {
                    Users newUser = request.toEntity();
                    return userRepository.save(newUser);
                });

        return LoginResponse.of(user);
    }

    @Transactional
    public NicknameUpdateResponse updateNickname(Long userId, NicknameUpdateRequest request) {
        Users user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        user.updateNickname(request.nickname());

        return NicknameUpdateResponse.of(user);
    }
}
