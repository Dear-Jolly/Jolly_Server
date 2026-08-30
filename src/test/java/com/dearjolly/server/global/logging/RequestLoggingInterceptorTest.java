package com.dearjolly.server.global.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.global.auth.principal.AuthenticatedUserHolder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(OutputCaptureExtension.class)
class RequestLoggingInterceptorTest {

    @DisplayName("인증 사용자의 요청 시작과 완료 정보를 같은 requestId로 기록한다.")
    @Test
    void logAuthenticatedRequest(CapturedOutput output) {
        // given
        AuthenticatedUserHolder holder = mock(AuthenticatedUserHolder.class);
        Users user = mock(Users.class);
        when(holder.get()).thenReturn(user);
        when(user.getId()).thenReturn(7L);
        when(user.getNickname()).thenReturn("jolly");
        RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor(holder);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/letters");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // when
        interceptor.preHandle(request, response, new Object());
        String requestId = response.getHeader(RequestLoggingInterceptor.REQUEST_ID_HEADER);
        response.setStatus(200);
        interceptor.afterCompletion(request, response, new Object(), null);

        // then
        assertThat(requestId).isNotBlank();
        assertThat(output).contains(
                "business_request_started",
                "method=GET",
                "path=/api/v1/letters",
                "business_request_completed",
                "status=200"
        );
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @DisplayName("실패한 요청은 상태 코드와 예외 종류를 기록한다.")
    @Test
    void logFailedRequest(CapturedOutput output) {
        // given
        AuthenticatedUserHolder holder = mock(AuthenticatedUserHolder.class);
        RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor(holder);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/letters");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException exception = new IllegalStateException("feedback invalid");

        // when
        interceptor.preHandle(request, response, new Object());
        response.setStatus(500);
        interceptor.afterCompletion(request, response, new Object(), exception);

        // then
        assertThat(output).contains(
                "business_request_completed",
                "status=500",
                "exception=IllegalStateException"
        );
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}
