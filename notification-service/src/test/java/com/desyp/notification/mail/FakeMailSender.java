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
    private volatile String crashOn;

    @Override
    public void send(String to, String subject, String body) {
        if (to.equals(crashOn)) {
            throw new Error("simulated crash");
        }
        sent.add(new SentMail(to, subject, body));
    }

    public List<SentMail> sent() {
        return Collections.unmodifiableList(sent);
    }

    public void crashOn(String to) {
        crashOn = to;
    }

    public void clear() {
        sent.clear();
        crashOn = null;
    }
}
