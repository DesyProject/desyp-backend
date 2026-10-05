package com.desyp.notification.conversion;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.desyp.notification.auth.SocialAccount;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ConversionController {

    private final ConversionService conversionService;

    // 사전 등록과 같이 JSON만 받아 CORS 사전 요청 대상으로 만든다. CSRF 토큰을 쓰지 않는 근거다.
    @PostMapping(value = "/api/conversions/kakao-channel-click", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void kakaoChannelClick(OAuth2AuthenticationToken authentication,
            @Valid @RequestBody KakaoChannelClickRequest request) {
        conversionService.recordKakaoChannelClick(SocialAccount.require(authentication), request.source());
    }
}
