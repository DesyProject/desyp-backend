package com.desip.notification.subscriber.exception;

import org.springframework.http.HttpStatus;

import com.desip.common.exception.BaseErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SubscriberErrorCode implements BaseErrorCode {

    CONSENT_REQUIRED(HttpStatus.BAD_REQUEST, "SUBSCRIBER_006", "연령 확인과 개인정보 수집 동의가 필요합니다"),
    VERIFIED_EMAIL_REQUIRED(HttpStatus.BAD_REQUEST, "SUBSCRIBER_007", "인증된 Google 이메일과 일치해야 합니다"),
    REGISTRATION_CONFLICT(HttpStatus.CONFLICT, "SUBSCRIBER_008", "등록 정보가 충돌합니다. 등록 여부를 확인해주세요"),
    GOOGLE_LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "SUBSCRIBER_009", "Google 로그인이 필요합니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "SUBSCRIBER_001", "이미 등록된 이메일입니다"),
    DUPLICATE_GOOGLE_ACCOUNT(HttpStatus.CONFLICT, "SUBSCRIBER_002", "이미 등록된 계정입니다"),
    INVALID_REFERRAL_CODE(HttpStatus.BAD_REQUEST, "SUBSCRIBER_003", "유효하지 않은 추천 코드입니다"),
    SELF_REFERRAL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SUBSCRIBER_004", "자기 자신을 추천할 수 없습니다"),
    SUBSCRIBER_NOT_FOUND(HttpStatus.NOT_FOUND, "SUBSCRIBER_005", "구독자를 찾을 수 없습니다");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;
}
