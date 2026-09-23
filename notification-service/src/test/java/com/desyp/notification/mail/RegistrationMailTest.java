package com.desyp.notification.mail;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.service.SubscriberService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RegistrationMailTest {

    @Autowired SubscriberRepository repository;
    @Autowired SubscriberService subscriberService;
    @Autowired RegistrationMailService registrationMailService;
    @Autowired FakeMailSender mailSender;

    @BeforeEach
    void clean() {
        repository.deleteAllInBatch();
        mailSender.clear();
    }

    private SubscriberRegisterRequest request() {
        return new SubscriberRegisterRequest(true, true, true, null);
    }

    @Test
    void sendsToEveryPendingSubscriberOnceAndSkipsAlreadyNotified() {
        subscriberService.register(new SocialAccount("a", "a@naver.com", "010-0000-0001"), request());
        subscriberService.register(new SocialAccount("b", "b@naver.com", "010-0000-0002"), request());

        int firstRun = registrationMailService.sendEventStartNotifications();
        assertThat(firstRun).isEqualTo(2);
        assertThat(mailSender.sent()).extracting(FakeMailSender.SentMail::to)
                .containsExactlyInAnyOrder("a@naver.com", "b@naver.com");
        assertThat(repository.findAll()).allSatisfy(subscriber -> assertThat(subscriber.getNotifiedAt()).isNotNull());

        int secondRun = registrationMailService.sendEventStartNotifications();
        assertThat(secondRun).isZero();
        assertThat(mailSender.sent()).hasSize(2);
    }
}
