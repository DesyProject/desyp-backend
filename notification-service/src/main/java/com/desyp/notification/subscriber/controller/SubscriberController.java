package com.desyp.notification.subscriber.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.desyp.notification.auth.SocialAccount;
import com.desyp.common.response.ApiResponse;
import com.desyp.notification.subscriber.dto.MeResponse;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.dto.SubscriberRegisterResponse;
import com.desyp.notification.subscriber.service.SubscriberService;
import lombok.RequiredArgsConstructor;


@RestController
@RequiredArgsConstructor
public class SubscriberController {

    private final SubscriberService subscriberService;

    // 프런트 계약에 맞춰 ApiResponse로 감싸지 않는다.
    @GetMapping("/api/me")
    public MeResponse me(OAuth2AuthenticationToken authentication) {
        return subscriberService.me(SocialAccount.require(authentication));
    }

    // JSON만 받아 교차 사이트 폼 전송을 CORS 사전 요청 대상으로 만든다. CSRF 토큰을 쓰지 않는 근거다.
    @PostMapping(value = "/api/pre-registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriberRegisterResponse> register(
            OAuth2AuthenticationToken authentication,
            @Valid @RequestBody SubscriberRegisterRequest request) {
        var account = SocialAccount.require(authentication);
        return ApiResponse.success(subscriberService.register(account, request));
    }
}
