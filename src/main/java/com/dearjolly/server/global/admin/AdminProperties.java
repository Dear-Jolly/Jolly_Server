package com.dearjolly.server.global.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dearjolly.admin")
public record AdminProperties(
        String username,
        String password
) {
}
