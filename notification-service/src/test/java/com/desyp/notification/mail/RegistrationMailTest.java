package com.desyp.notification.mail;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.service.SubscriberService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        subscriberService.register(new SocialAccount("a", "a@naver.com"), request());
        subscriberService.register(new SocialAccount("b", "b@naver.com"), request());

        int firstRun = registrationMailService.sendEventStartNotifications();
        assertThat(firstRun).isEqualTo(2);
        assertThat(mailSender.sent()).extracting(FakeMailSender.SentMail::to)
                .containsExactlyInAnyOrder("a@naver.com", "b@naver.com");
        assertThat(mailSender.sent()).allSatisfy(mail -> assertThat(mail.body()).contains("https://pf.kakao.com/_ExbgxiX"));
        assertThat(repository.findAll()).allSatisfy(subscriber -> assertThat(subscriber.getNotifiedAt()).isNotNull());

        int secondRun = registrationMailService.sendEventStartNotifications();
        assertThat(secondRun).isZero();
        assertThat(mailSender.sent()).hasSize(2);
    }

    @Test
    void keepsNotifiedRecordsOfMailsSentBeforeCrash() {
        subscriberService.register(new SocialAccount("a", "a@naver.com"), request());
        subscriberService.register(new SocialAccount("b", "b@naver.com"), request());
        mailSender.crashOn("b@naver.com");

        assertThatThrownBy(registrationMailService::sendEventStartNotifications).isInstanceOf(Error.class);

        assertThat(repository.findByProviderAndProviderAccountId(AuthProvider.NAVER, "a").orElseThrow().getNotifiedAt())
                .isNotNull();
        mailSender.clear();
        assertThat(registrationMailService.sendEventStartNotifications()).isEqualTo(1);
        assertThat(mailSender.sent()).extracting(FakeMailSender.SentMail::to).containsExactly("b@naver.com");
    }

    @Test
    void concurrentRunsSendEachMailOnce() {
        for (int i = 0; i < 30; i++) {
            subscriberService.register(new SocialAccount("u" + i, "u" + i + "@naver.com"), request());
        }

        var first = CompletableFuture.supplyAsync(registrationMailService::sendEventStartNotifications);
        var second = CompletableFuture.supplyAsync(registrationMailService::sendEventStartNotifications);

        assertThat(first.join() + second.join()).isEqualTo(30);
        assertThat(mailSender.sent()).hasSize(30);
    }
}
