package com.dearjolly.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 인증은 JWT 로만 처리하고 form login·basic auth 를 모두 끄므로
 * UserDetailsService 가 필요 없다. 자동 구성을 그대로 두면 기동할 때마다
 * 쓰지도 않는 기본 유저와 임시 비밀번호가 로그에 찍힌다.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
@EnableScheduling
public class ServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}
