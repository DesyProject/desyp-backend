package com.desyp.notification.auth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRedirectTest {

    private final LoginRedirect redirect = new LoginRedirect("https://www.desyp.site");

    private String failWith(Exception exception) throws Exception {
        var request = new MockHttpServletRequest();
        redirect.remember(request.getSession(), "https://www.desyp.site/?login=error&reason=old#entry-card");
        var response = new MockHttpServletResponse();
        redirect.onAuthenticationFailure(request, response, (org.springframework.security.core.AuthenticationException) exception);
        return response.getRedirectedUrl();
    }

    @Test
    void failureRedirectsToReturnToWithReason() throws Exception {
        assertThat(failWith(new OAuth2AuthenticationException(new OAuth2Error("no_email"))))
                .isEqualTo("https://www.desyp.site/?login=error&reason=no_email#entry-card");
        assertThat(failWith(new OAuth2AuthenticationException(new OAuth2Error("no_phone")))).contains("reason=no_phone");
        assertThat(failWith(new OAuth2AuthenticationException(new OAuth2Error("access_denied")))).contains("reason=cancelled");
        assertThat(failWith(new BadCredentialsException("x"))).contains("reason=failed");
    }

    @Test
    void successRedirectsToRememberedReturnToOnce() throws Exception {
        var request = new MockHttpServletRequest();
        redirect.remember(request.getSession(), "https://www.desyp.site/#entry-card");
        var response = new MockHttpServletResponse();
        redirect.onAuthenticationSuccess(request, response, null);
        assertThat(response.getRedirectedUrl()).isEqualTo("https://www.desyp.site/#entry-card");

        var again = new MockHttpServletResponse();
        redirect.onAuthenticationSuccess(request, again, null);
        assertThat(again.getRedirectedUrl()).isEqualTo("https://www.desyp.site/");
    }
}
