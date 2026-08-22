package com.dearjolly.server.support;

import com.dearjolly.server.domain.user.entity.TermsAgreements;
import com.dearjolly.server.domain.user.entity.Users;
import com.dearjolly.server.domain.user.enums.OauthProvider;
import com.dearjolly.server.domain.user.enums.TermsType;
import com.dearjolly.server.domain.user.repository.TermsAgreementRepository;
import com.dearjolly.server.domain.user.repository.UserRepository;
import com.dearjolly.server.global.auth.jwt.JwtProvider;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public abstract class ApiTestSupport {
    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TermsAgreementRepository termsAgreementRepository;

    @Autowired
    protected JwtProvider jwtProvider;

    @BeforeEach
    void setUpPort() {
        RestAssured.port = port;
    }

    protected Users 유저를_저장한다(String oauthId) {
        return userRepository.save(Users.create(OauthProvider.KAKAO, oauthId, "jolly@example.com"));
    }

    protected Users 온보딩을_마친_유저를_저장한다(String oauthId, String nickname) {
        Users user = 유저를_저장한다(oauthId);
        user.updateNickname(nickname);
        필수약관에_동의한다(user);
        return userRepository.save(user);
    }

    protected void 필수약관에_동의한다(Users user) {
        termsAgreementRepository.save(TermsAgreements.create(user, TermsType.SERVICE, true, "1.0.0"));
        termsAgreementRepository.save(TermsAgreements.create(user, TermsType.PRIVACY, true, "1.0.0"));
    }

    protected String 액세스토큰(Users user) {
        return "Bearer " + jwtProvider.createAccessToken(user.getId(), user.getRole());
    }
}
