package com.dearjolly.server.domain.feedback.service;

import java.util.List;

public interface LlmClient {
    LlmFeedback correct(String content, List<String> stampNames);
}
