package com.desyp.notification.conversion;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.subscriber.repository.SubscriberRepository;

import lombok.RequiredArgsConstructor;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SUBSCRIBER_NOT_FOUND;

@Service
@RequiredArgsConstructor
public class ConversionService {

    private final SubscriberRepository subscriberRepository;
    private final KakaoChannelClickRepository kakaoChannelClickRepository;

    @Transactional
    public void recordKakaoChannelClick(SocialAccount account, ConversionSource source) {
        var subscriber = subscriberRepository.findByProviderAndProviderAccountId(AuthProvider.NAVER, account.accountId())
                .orElseThrow(() -> new BusinessException(SUBSCRIBER_NOT_FOUND));
        kakaoChannelClickRepository.insertIfAbsent(subscriber.getId(), source.name());
    }
}
