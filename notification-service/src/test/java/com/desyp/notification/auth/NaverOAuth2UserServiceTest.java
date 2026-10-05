package com.desyp.notification.auth;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class NaverOAuth2UserServiceTest {

    private static final String USER_INFO = "https://openapi.naver.com/v1/nid/me";

    private OAuth2UserRequest request() {
        var registration = ClientRegistration.withRegistrationId("naver")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/auth/naver/callback")
                .scope("email")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri(USER_INFO)
                .userNameAttributeName("response").build();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "token",
                Instant.now(), Instant.now().plusSeconds(60));
        return new OAuth2UserRequest(registration, token);
    }

    @Test
    void logsInWithoutMobileButRequiresEmail() {
        var rest = new RestTemplate();
        var server = MockRestServiceServer.bindTo(rest).build();
        var service = new NaverOAuth2UserService();
        service.setRestOperations(rest);
        server.expect(requestTo(USER_INFO)).andRespond(withSuccess("""
                {"resultcode":"00","response":{"id":"naver-1","email":"user@naver.com"}}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(USER_INFO)).andRespond(withSuccess("""
                {"resultcode":"00","response":{"id":"naver-2"}}
                """, MediaType.APPLICATION_JSON));

        var user = service.loadUser(request());
        assertThat(user.getName()).isEqualTo("naver-1");
        assertThat(user.<String>getAttribute("email")).isEqualTo("user@naver.com");
        assertThatThrownBy(() -> service.loadUser(request()))
                .isInstanceOfSatisfying(OAuth2AuthenticationException.class,
                        e -> assertThat(e.getError().getErrorCode()).isEqualTo(NaverOAuth2UserService.NO_EMAIL));
        server.verify();
    }
}
