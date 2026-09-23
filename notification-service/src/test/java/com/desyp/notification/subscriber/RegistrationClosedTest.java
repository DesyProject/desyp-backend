package com.desyp.notification.subscriber;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.service.SubscriberService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "desyp.registration.end-at=2020-01-01T00:00:00+09:00")
class RegistrationClosedTest {

    @Autowired SubscriberService service;
    @Autowired SubscriberRepository repository;

    @Test
    void rejectsRegistrationAfterDeadlineWithGone() {
        repository.deleteAllInBatch();
        assertThatThrownBy(() -> service.register(new SocialAccount("late", "late@naver.com", "010-8000-0001"),
                new SubscriberRegisterRequest(true, true, true, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode().getHttpStatus()).isEqualTo(HttpStatus.GONE));
        assertThat(repository.count()).isZero();
    }
}
