package com.desyp.notification.mail;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.desyp.notification.subscriber.entity.Subscriber;
import com.desyp.notification.subscriber.repository.SubscriberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationMailService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationMailService.class);
    private static final String SUBJECT = "[desyp] 이벤트가 곧 시작됩니다";
    private static final String BODY = "사전 등록해주셔서 감사합니다. 이벤트가 곧 시작됩니다.";

    private final SubscriberRepository subscriberRepository;
    private final MailSender mailSender;

    @Transactional
    public int sendEventStartNotifications() {
        var pending = subscriberRepository.findByNotifiedAtIsNull();
        int sent = 0;
        for (Subscriber subscriber : pending) {
            try {
                mailSender.send(subscriber.getEmail(), SUBJECT, BODY);
                subscriber.markNotified(OffsetDateTime.now(ZoneOffset.UTC));
                sent++;
            } catch (RuntimeException exception) {
                log.warn("이벤트 알림 메일 발송 실패: subscriberId={}", subscriber.getId(), exception);
            }
        }
        return sent;
    }
}
