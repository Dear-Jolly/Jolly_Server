package com.dearjolly.server.global.config;

import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtAuthenticationFilter;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import com.dearjolly.server.global.exception.response.ErrorCode;
import com.dearjolly.server.global.exception.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 인증 없이 접근 가능한 경로 (API명세 §2.1) */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/reissue",
            "/api/v1/version",
            "/actuator/health",
            "/error",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 소셜 로그인 시작·콜백은 토큰 없이 들어온다.
                        // 메서드까지 함께 지정해야 POST /api/v1/auth/logout 이 열리지 않는다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/*/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/*/callback").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtProvider, userRepository, objectMapper),
                        UsernamePasswordAuthenticationFilter.class
                )
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((req, res, e) -> writeError(res, ErrorCode.ACCESS_TOKEN_INVALID))
                        .accessDeniedHandler((req, res, e) -> writeError(res, ErrorCode.ACCESS_DENIED))
                )
                .build();
    }

    private void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ErrorResponse.toResponseEntity(errorCode).getBody()
        ));
    }
}
