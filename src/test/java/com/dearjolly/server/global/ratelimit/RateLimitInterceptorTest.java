package com.dearjolly.server.global.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import com.dearjolly.server.global.exception.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class RateLimitInterceptorTest {
    private final AuthenticatedUserHolder holder = mock(AuthenticatedUserHolder.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(holder);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @Test
    void letterCreateIsLimitedPerUser() {
        Users user = mock(Users.class);
        when(user.getId()).thenReturn(7L);
        when(holder.get()).thenReturn(user);
        HttpServletRequest request = request("POST", "/api/v1/letters", "127.0.0.1");

        for (int i = 0; i < 5; i++) {
            interceptor.preHandle(request, response, new Object());
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> org.assertj.core.api.Assertions.assertThat(exception.getErrorCode().getCode())
                                .isEqualTo("COMMON_004"));
    }

    @Test
    void allApiRequestsAreLimitedPerClient() {
        HttpServletRequest request = request("GET", "/api/v1/version", "127.0.0.2");

        for (int i = 0; i < 120; i++) {
            interceptor.preHandle(request, response, new Object());
        }

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(BusinessException.class);
    }

    private HttpServletRequest request(String method, String uri, String remoteAddress) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn(method);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getRemoteAddr()).thenReturn(remoteAddress);
        return request;
    }
}
