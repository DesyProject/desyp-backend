package com.desyp.notification.auth;

import java.util.Map;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.subscriber.util.PhoneNumberNormalizer;

/**
 * 네이버 사용자 정보는 최상위가 아니라 "response" 객체 안에 담겨온다.
 * 기본 {@link DefaultOAuth2UserService}는 이를 그대로 attributes로 노출해 id/email 접근이 불가능해서 평탄화한다.
 * 이메일이나 휴대전화번호 제공을 거부하면 세션을 만들지 않고 로그인 실패로 처리한다.
 */
@Component
public class NaverOAuth2UserService extends DefaultOAuth2UserService {

    static final String NO_EMAIL = "no_email";
    static final String NO_PHONE = "no_phone";

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User user = super.loadUser(userRequest);
        if (!"naver".equals(userRequest.getClientRegistration().getRegistrationId())) {
            return user;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) user.getAttributes().get("response");
        if (response == null || !StringUtils.hasText((String) response.get("email"))) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NO_EMAIL));
        }
        try {
            PhoneNumberNormalizer.normalize((String) response.get("mobile"));
        } catch (BusinessException exception) {
            throw new OAuth2AuthenticationException(new OAuth2Error(NO_PHONE));
        }
        return new DefaultOAuth2User(user.getAuthorities(), response, "id");
    }
}
