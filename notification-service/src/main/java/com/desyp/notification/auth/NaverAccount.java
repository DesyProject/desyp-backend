package com.desyp.notification.auth;

import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SOCIAL_LOGIN_REQUIRED;
import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.VERIFIED_EMAIL_REQUIRED;

public final class NaverAccount {
    private NaverAccount() {
    }

    public static SocialAccount require(OAuth2AuthenticationToken authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || !"naver".equals(authentication.getAuthorizedClientRegistrationId())) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        var user = authentication.getPrincipal();
        String id = user.getAttribute("id");
        String email = user.getAttribute("email");
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        // 네이버는 email_verified 클레임을 주지 않는다. email scope는 휴대폰 인증된 계정에만 승인되므로
        // 값이 존재하면 검증된 이메일로 취급한다.
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        return new SocialAccount(AuthProvider.NAVER, id, email);
    }
}
