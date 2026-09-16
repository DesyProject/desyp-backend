package com.desyp.notification.subscriber.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.desyp.notification.auth.SocialAccount;
import com.desyp.common.response.ApiResponse;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.dto.SubscriberRegisterResponse;
import com.desyp.notification.subscriber.service.SubscriberService;
import lombok.RequiredArgsConstructor;


@RestController
@RequestMapping("/api/subscribers")
@RequiredArgsConstructor
public class SubscriberController {

    private final SubscriberService subscriberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriberRegisterResponse> register(
            OAuth2AuthenticationToken authentication,
            @Valid @RequestBody SubscriberRegisterRequest request) {
        var account = SocialAccount.require(authentication);
        return ApiResponse.success(subscriberService.register(
                account.provider(), account.accountId(), account.email(), request));
    }
}
