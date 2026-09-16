package com.desyp.notification.subscriber.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.*;

import com.desyp.common.exception.BusinessException;
import com.desyp.common.response.ApiResponse;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.dto.SubscriberRegisterResponse;
import com.desyp.notification.subscriber.service.SubscriberService;
import lombok.RequiredArgsConstructor;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.*;

@RestController
@RequestMapping("/api/subscribers")
@RequiredArgsConstructor
public class SubscriberController {

    private final SubscriberService subscriberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SubscriberRegisterResponse> register(
            @AuthenticationPrincipal OidcUser user,
            OAuth2AuthenticationToken authentication,
            @Valid @RequestBody SubscriberRegisterRequest request) {
        if (user == null || authentication == null
                || !"google".equals(authentication.getAuthorizedClientRegistrationId())) {
            throw new BusinessException(GOOGLE_LOGIN_REQUIRED);
        }
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        return ApiResponse.success(subscriberService.register(user.getSubject(), user.getEmail(), request));
    }
}
