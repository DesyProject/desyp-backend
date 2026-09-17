package com.desyp.notification.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SOCIAL_LOGIN_REQUIRED;
import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.VERIFIED_EMAIL_REQUIRED;

public record SocialAccount(AuthProvider provider, String accountId, String email) {

    public static SocialAccount require(OAuth2AuthenticationToken authentication) {
        String registrationId = authentication == null ? null : authentication.getAuthorizedClientRegistrationId();
        if ("google".equals(registrationId)) {
            var user = GoogleAccount.require(authentication);
            return new SocialAccount(AuthProvider.GOOGLE, user.getSubject(), user.getEmail());
        }
        if ("naver".equals(registrationId)) {
            return requireNaver(authentication);
        }
        throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
    }

    private static SocialAccount requireNaver(OAuth2AuthenticationToken authentication) {
        if (!authentication.isAuthenticated()) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        var user = authentication.getPrincipal();
        String id = user.getAttribute("id");
        String email = user.getAttribute("email");
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        // 네이버에는 email_verified 클레임이 없어 email scope의 값 존재 여부를 검증 기준으로 사용한다.
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        return new SocialAccount(AuthProvider.NAVER, id, email);
    }
}
