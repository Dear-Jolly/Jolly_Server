package com.dearjolly.server.global.auth.jwt;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.principal.AuthUser;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 토큰 서명·만료를 검증한 뒤 계정 상태까지 확인한다.
 * 탈퇴 처리된 계정의 토큰은 AUTH_007 로 거절한다 — 탈퇴 직후 최대 30분간
 * 유효한 Access Token 이 남아 있기 때문이다 (API명세 §2.1).
 *
 * <p>인증이 필요 없는 경로는 {@link #shouldNotFilter} 로 건너뛴다. Security 의
 * {@code permitAll()} 은 인가만 열어줄 뿐 필터 실행을 막지 못하므로 경로 목록을 직접 본다.
 * 재발급은 정의상 만료된 Access Token 을 들고 오는 요청이라 이 구분이 필수다 (API명세 §3.2).
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final List<String> skipPathPatterns;

    /** 인증 없이 통과해야 하는 경로는 토큰을 아예 들여다보지 않는다. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return skipPathPatterns.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> token = resolveToken(request);
        if (token.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            userId = jwtProvider.getUserId(token.get());
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        Optional<Users> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        Users user = found.get();
        if (user.isWithdrawn()) {
            writeError(response, ErrorCode.WITHDRAWN_USER);
            return;
        }

        AuthUser authUser = new AuthUser(user.getId(), user.getRole());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authUser,
                        null,
                        List.of(new SimpleGrantedAuthority(user.getRole().name()))
                )
        );
        filterChain.doFilter(request, response);
    }

    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        SecurityContextHolder.clearContext();
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.toResponseEntity(errorCode).getBody()
        ));
    }
}
