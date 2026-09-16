package com.desyp.notification.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import com.desyp.common.exception.BusinessException;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SOCIAL_LOGIN_REQUIRED;

public record SocialAccount(AuthProvider provider, String accountId, String email) {

    public static SocialAccount require(OAuth2AuthenticationToken authentication) {
        String registrationId = authentication == null ? null : authentication.getAuthorizedClientRegistrationId();
        if ("google".equals(registrationId)) {
            var user = GoogleAccount.require(authentication);
            return new SocialAccount(AuthProvider.GOOGLE, user.getSubject(), user.getEmail());
        }
        if ("naver".equals(registrationId)) {
            return NaverAccount.require(authentication);
        }
        throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
    }
}
