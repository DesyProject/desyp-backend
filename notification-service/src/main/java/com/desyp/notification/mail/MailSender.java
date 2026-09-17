package com.desyp.notification.mail;

public interface MailSender {
    void send(String to, String subject, String body);
}
