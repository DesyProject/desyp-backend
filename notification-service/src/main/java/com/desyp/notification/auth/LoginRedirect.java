package com.desyp.notification.auth;

import java.io.IOException;
import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 로그인 시작 시 받은 return_to를 세션에 두고, 네이버 콜백 처리 후 그 주소로 돌려보낸다.
 * 프런트 Origin이 아닌 주소는 열린 리다이렉트를 막기 위해 프런트 루트로 바꾼다.
 */
@Component
public class LoginRedirect implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final String RETURN_TO = LoginRedirect.class.getName() + ".RETURN_TO";

    private final URI frontendOrigin;

    public LoginRedirect(@Value("${desyp.frontend.origin}") String frontendOrigin) {
        this.frontendOrigin = URI.create(frontendOrigin);
    }

    public void remember(HttpSession session, String returnTo) {
        session.setAttribute(RETURN_TO, isAllowed(returnTo) ? returnTo : frontendOrigin + "/");
    }

    boolean isAllowed(String returnTo) {
        if (returnTo == null) return false;
        try {
            URI uri = URI.create(returnTo);
            return frontendOrigin.getScheme().equals(uri.getScheme())
                    && frontendOrigin.getHost().equalsIgnoreCase(uri.getHost())
                    && frontendOrigin.getPort() == uri.getPort()
                    && uri.getRawUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException {
        response.sendRedirect(takeReturnTo(request));
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.sendRedirect(UriComponentsBuilder.fromUriString(takeReturnTo(request))
                .replaceQueryParam("login", "error")
                .replaceQueryParam("reason", reason(exception))
                .build().toUriString());
    }

    private String reason(AuthenticationException exception) {
        if (!(exception instanceof OAuth2AuthenticationException oauth)) return "failed";
        return switch (oauth.getError().getErrorCode()) {
            case NaverOAuth2UserService.NO_EMAIL, NaverOAuth2UserService.NO_PHONE -> oauth.getError().getErrorCode();
            case "access_denied" -> "cancelled";
            default -> "failed";
        };
    }

    private String takeReturnTo(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object returnTo = session == null ? null : session.getAttribute(RETURN_TO);
        if (session != null) session.removeAttribute(RETURN_TO);
        return returnTo instanceof String value ? value : frontendOrigin + "/";
    }
}
