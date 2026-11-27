package com.desyp.notification.mail;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.desyp.notification.subscriber.entity.Subscriber;
import com.desyp.notification.subscriber.repository.SubscriberRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationMailService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationMailService.class);
    // ponytail: 이벤트 일정과 주소를 고정 문구로 둔다. 이벤트가 또 열리면 설정값으로 뺀다
    private static final String SUBJECT = "[De_sy_P] 오늘 오후 2시, 단 10분! 이벤트가 곧 시작됩니다";
    private static final String BODY = """
            사전 등록해주셔서 감사합니다.

            De_sy_P 메인 이벤트가 11월 29일(일) 오후 2시부터 단 10분 동안 열립니다.
            로그인 없이 Instagram 아이디만 입력하면 응모할 수 있어요.

            이벤트 참여하기
            https://www.desyp.site

            일정과 공지를 카카오톡으로도 받아보세요.
            https://pf.kakao.com/_ExbgxiX
            """;

    private final SubscriberRepository subscriberRepository;
    private final MailSender mailSender;
    private final TransactionTemplate transactionTemplate;

    /**
     * 수신자마다 트랜잭션을 나눠 중간에 서버가 죽어도 이미 보낸 기록은 남긴다.
     * 행 잠금으로 동시 실행이 같은 수신자에게 보내지 않게 한다.
     * 발송 직후 커밋 전에 죽으면 그 한 명만 다음 실행에서 다시 받는다.
     */
    public int sendEventStartNotifications() {
        int sent = 0;
        for (Long id : subscriberRepository.findPendingNotificationIds()) {
            if (Boolean.TRUE.equals(transactionTemplate.execute(status -> sendIfPending(id)))) {
                sent++;
            }
        }
        return sent;
    }

    private boolean sendIfPending(Long id) {
        Subscriber subscriber = subscriberRepository.findByIdForUpdate(id).orElse(null);
        if (subscriber == null || subscriber.getNotifiedAt() != null) {
            return false;
        }
        try {
            mailSender.send(subscriber.getEmail(), SUBJECT, BODY);
        } catch (RuntimeException exception) {
            // SES 오류 메시지에 수신자 이메일이 들어갈 수 있어 예외 종류만 남긴다.
            log.warn("이벤트 알림 메일 발송 실패: subscriberId={}, error={}", id, exception.getClass().getSimpleName());
            return false;
        }
        subscriber.markNotified(OffsetDateTime.now(ZoneOffset.UTC));
        return true;
    }
}
