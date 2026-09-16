package com.desyp.notification.subscriber.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.dto.SubscriberRegisterResponse;
import com.desyp.notification.subscriber.entity.Subscriber;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.util.EmailNormalizer;

import lombok.RequiredArgsConstructor;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.*;

@Service
@RequiredArgsConstructor
public class SubscriberService {

    private final SubscriberRepository subscriberRepository;

    @Transactional
    public SubscriberRegisterResponse register(AuthProvider provider, String providerAccountId, String verifiedEmail,
                                               SubscriberRegisterRequest request) {
        if (provider == null || !StringUtils.hasText(providerAccountId)) {
            throw new BusinessException(SOCIAL_LOGIN_REQUIRED);
        }
        if (!Boolean.TRUE.equals(request.ageConfirmed()) || !Boolean.TRUE.equals(request.privacyConsented())) {
            throw new BusinessException(CONSENT_REQUIRED);
        }
        if (!StringUtils.hasText(verifiedEmail) || !verifiedEmail.equalsIgnoreCase(request.email())) {
            throw new BusinessException(VERIFIED_EMAIL_REQUIRED);
        }
        String normalized = EmailNormalizer.normalize(verifiedEmail);
        Subscriber referrer = null;
        if (StringUtils.hasText(request.referralCode())) {
            referrer = subscriberRepository.findByInviteToken(request.referralCode())
                    .orElseThrow(() -> new BusinessException(INVALID_REFERRAL_CODE));
            boolean sameAccount = provider == referrer.getProvider()
                    && providerAccountId.equals(referrer.getProviderAccountId());
            if (sameAccount || normalized.equals(referrer.getEmailNormalized())) {
                throw new BusinessException(SELF_REFERRAL_NOT_ALLOWED);
            }
        }
        if (subscriberRepository.existsByProviderAndProviderAccountId(provider, providerAccountId)) {
            throw new BusinessException(DUPLICATE_SOCIAL_ACCOUNT);
        }
        if (subscriberRepository.existsByEmailNormalized(normalized)) {
            throw new BusinessException(DUPLICATE_EMAIL);
        }
        // 추천인은 신규 등록 시에만 지정한다. 기존 관계를 수정하지 않아 순환 추천을 방지한다.
        Subscriber subscriber = Subscriber.builder()
                .provider(provider)
                .providerAccountId(providerAccountId)
                .email(verifiedEmail)
                .emailNormalized(normalized)
                .referrer(referrer)
                .inviteToken(UUID.randomUUID().toString())
                .ageConfirmed(true)
                .consentAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
        try {
            subscriberRepository.saveAndFlush(subscriber);
        } catch (DataIntegrityViolationException exception) {
            // 사전 조회 이후의 동시 등록도 DB UNIQUE 제약으로 차단한다.
            throw new BusinessException(REGISTRATION_CONFLICT);
        }
        return new SubscriberRegisterResponse(subscriber.getId(), subscriber.getInviteToken());
    }
}
