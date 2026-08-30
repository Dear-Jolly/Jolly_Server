package com.dearjolly.server.domain.feedback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("llmstub")
class SpringAiLlmClientRequestTest {
    private static final List<String> STAMP_NAMES = List.of("꽃_장미", "친구_위로", "맥주");
    private static final AtomicReference<String> LAST_REQUEST_BODY = new AtomicReference<>();

    private static final HttpServer STUB = startStub();

    @Autowired
    private LlmClient llmClient;

    @DynamicPropertySource
    static void openAiBaseUrl(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://localhost:" + STUB.getAddress().getPort());
    }

    @AfterAll
    static void stopStub() {
        STUB.stop(0);
    }

    @DisplayName("우표 후보를 응답 스키마의 enum 으로 내려 다른 값이 나올 수 없게 한다.")
    @Test
    void sendStampCandidatesAsSchemaEnum() throws IOException {
        // when
        LlmFeedback feedback = llmClient.correct("I go to school yesterday.", STAMP_NAMES);

        // then
        JsonNode request = new ObjectMapper().readTree(LAST_REQUEST_BODY.get());
        JsonNode schema = request.path("response_format").path("json_schema");
        assertThat(request.path("response_format").path("type").asText()).isEqualTo("json_schema");
        assertThat(schema.path("strict").asBoolean()).isTrue();

        JsonNode properties = schema.path("schema").path("properties");
        assertThat(properties.path("stampName").path("enum"))
                .extracting(JsonNode::asText)
                .containsExactlyElementsOf(STAMP_NAMES);
        assertThat(schema.path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("schema").path("required"))
                .extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("correctedContent", "tips", "stampName");
        assertThat(feedback.stampName()).isEqualTo("꽃_장미");
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> {
                LAST_REQUEST_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] body = COMPLETION_RESPONSE.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("OpenAI 스텁 서버를 띄우지 못했다.", e);
        }
    }

    private static final String COMPLETION_RESPONSE = """
            {
              "id": "chatcmpl-stub",
              "object": "chat.completion",
              "created": 1,
              "model": "gpt-4o-mini-2024-07-18",
              "choices": [
                {
                  "index": 0,
                  "finish_reason": "stop",
                  "message": {
                    "role": "assistant",
                    "content": "{\\"correctedContent\\":\\"I went to school yesterday.\\",\\"tips\\":[\\"과거형은 went 예요.\\"],\\"stampName\\":\\"꽃_장미\\"}"
                  }
                }
              ]
            }
            """;
}
