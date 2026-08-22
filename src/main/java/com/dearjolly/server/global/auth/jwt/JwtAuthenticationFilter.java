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

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final List<String> skipPathPatterns;

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

        JwtPayload payload;
        try {
            payload = jwtProvider.payloadOf(token.get());
        } catch (JwtException | IllegalArgumentException e) {
            writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        Optional<Users> found = userRepository.findById(payload.userId());
        if (found.isEmpty()) {
            writeError(response, ErrorCode.ACCESS_TOKEN_INVALID);
            return;
        }

        Users user = found.get();
        if (user.isWithdrawn()) {
            writeError(response, ErrorCode.WITHDRAWN_USER);
            return;
        }

        authenticate(new AuthUser(user.getId(), user.getRole()));
        filterChain.doFilter(request, response);
    }

    private void authenticate(AuthUser authUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        authUser,
                        null,
                        List.of(new SimpleGrantedAuthority(authUser.role().name()))
                )
        );
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
