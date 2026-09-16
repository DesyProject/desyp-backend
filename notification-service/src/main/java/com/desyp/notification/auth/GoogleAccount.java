package com.desyp.notification.auth;

import com.desyp.common.exception.BusinessException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.util.StringUtils;
import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.*;

public final class GoogleAccount {
    private GoogleAccount() {
    }

    public static OidcUser require(OAuth2AuthenticationToken authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !"google".equals(authentication.getAuthorizedClientRegistrationId())
                || !(authentication.getPrincipal() instanceof OidcUser user)
                || !StringUtils.hasText(user.getSubject())) {
            throw new BusinessException(GOOGLE_LOGIN_REQUIRED);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        return user;
    }
}
