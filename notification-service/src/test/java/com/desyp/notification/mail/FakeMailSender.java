package com.desyp.notification.mail;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class FakeMailSender implements MailSender {

    public record SentMail(String to, String subject, String body) {
    }

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new SentMail(to, subject, body));
    }

    public List<SentMail> sent() {
        return Collections.unmodifiableList(sent);
    }

    public void clear() {
        sent.clear();
    }
}
