package com.desyp.notification.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SOCIAL_LOGIN_REQUIRED;
import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.VERIFIED_EMAIL_REQUIRED;
import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.VERIFIED_PHONE_REQUIRED;

public record SocialAccount(String accountId, String email, String phoneNumber) {

    public static SocialAccount require(OAuth2AuthenticationToken authentication) {
        String registrationId = authentication == null ? null : authentication.getAuthorizedClientRegistrationId();
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
        String phoneNumber = user.getAttribute("mobile");
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        // 네이버에는 email_verified 클레임이 없어 email scope의 값 존재 여부를 검증 기준으로 사용한다.
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        if (!StringUtils.hasText(phoneNumber)) {
            throw new BusinessException(VERIFIED_PHONE_REQUIRED);
        }
        return new SocialAccount(id, email, phoneNumber);
    }
}
