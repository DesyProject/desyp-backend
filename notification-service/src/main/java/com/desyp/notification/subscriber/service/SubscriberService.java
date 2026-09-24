package com.desyp.notification.subscriber.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.subscriber.dto.MeResponse;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.dto.SubscriberRegisterResponse;
import com.desyp.notification.subscriber.entity.Subscriber;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.util.EmailNormalizer;
import com.desyp.notification.subscriber.util.PhoneNumberNormalizer;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.*;

@Service
public class SubscriberService {

    // 혼동하기 쉬운 0/O, 1/I/L을 뺀 대문자·숫자 31자. 8자리면 약 8,500억 가지다.
    private static final String REFERRAL_CODE_CHARS = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int REFERRAL_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SubscriberRepository subscriberRepository;
    private final OffsetDateTime registrationEndAt;

    public SubscriberService(SubscriberRepository subscriberRepository,
            @Value("${desyp.registration.end-at}") OffsetDateTime registrationEndAt) {
        this.subscriberRepository = subscriberRepository;
        this.registrationEndAt = registrationEndAt;
    }

    @Transactional(readOnly = true)
    public MeResponse me(SocialAccount account) {
        boolean registered = subscriberRepository.existsByProviderAndProviderAccountId(AuthProvider.NAVER, account.accountId());
        return new MeResponse(mask(account.email()), registered);
    }

    @Transactional
    public SubscriberRegisterResponse register(SocialAccount account, SubscriberRegisterRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (now.isAfter(registrationEndAt)) {
            throw new BusinessException(REGISTRATION_CLOSED);
        }
        if (account == null || !StringUtils.hasText(account.accountId())) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        if (!Boolean.TRUE.equals(request.ageConfirmed()) || !Boolean.TRUE.equals(request.agreePrivacy())
                || !Boolean.TRUE.equals(request.agreeMarketing())) {
            throw new BusinessException(CONSENT_REQUIRED);
        }
        if (!StringUtils.hasText(account.email())) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        String normalizedEmail = EmailNormalizer.normalize(account.email());
        String normalizedPhone = PhoneNumberNormalizer.normalize(account.phoneNumber());
        if (subscriberRepository.existsByProviderAndProviderAccountId(AuthProvider.NAVER, account.accountId())) {
            throw new BusinessException(DUPLICATE_SOCIAL_ACCOUNT);
        }
        if (subscriberRepository.existsByEmailNormalized(normalizedEmail)) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }
        if (subscriberRepository.existsByPhoneNumber(normalizedPhone)) {
            throw new BusinessException(DUPLICATE_PHONE);
        }
        Subscriber referrer = null;
        if (StringUtils.hasText(request.referralCode())) {
            referrer = subscriberRepository.findByReferralCode(request.referralCode().trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new BusinessException(REFERRER_NOT_FOUND));
        }
        // 추천인은 신규 등록 시에만 지정한다. 기존 관계를 수정하지 않아 순환 추천을 방지한다.
        Subscriber subscriber = Subscriber.builder()
                .provider(AuthProvider.NAVER)
                .providerAccountId(account.accountId())
                .email(account.email())
                .emailNormalized(normalizedEmail)
                .phoneNumber(normalizedPhone)
                .referrer(referrer)
                .referralCode(newReferralCode())
                .ageConfirmed(true)
                .privacyAgreed(true)
                .marketingAgreed(true)
                .consentAt(now)
                .build();
        try {
            subscriberRepository.saveAndFlush(subscriber);
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 이후의 동시 등록도 DB UNIQUE 제약으로 차단한다.
            throw new BusinessException(REGISTRATION_CONFLICT);
        }
        return new SubscriberRegisterResponse(subscriber.getId(), subscriber.getReferralCode());
    }

    private String newReferralCode() {
        // ponytail: 사전 조회 후 저장 사이의 동시 충돌은 UNIQUE 제약이 REGISTRATION_CONFLICT로 막는다. 확률이 무시할 수준이라 재시도하지 않는다.
        String code;
        do {
            code = randomReferralCode();
        } while (subscriberRepository.existsByReferralCode(code));
        return code;
    }

    static String randomReferralCode() {
        var code = new StringBuilder(REFERRAL_CODE_LENGTH);
        for (int i = 0; i < REFERRAL_CODE_LENGTH; i++) {
            code.append(REFERRAL_CODE_CHARS.charAt(RANDOM.nextInt(REFERRAL_CODE_CHARS.length())));
        }
        return code.toString();
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 0) return "***";
        String local = email.substring(0, at);
        String visible = local.length() <= 3 ? local.substring(0, Math.min(1, local.length())) : local.substring(0, 3);
        return visible + "***" + email.substring(at);
    }
}
