package com.desyp.notification.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;

@Component
@Profile("!test")
public class SesMailSender implements MailSender {

    private final SesClient sesClient;
    private final String senderEmail;

    public SesMailSender(SesClient sesClient, @Value("${desyp.mail.sender-email}") String senderEmail) {
        this.sesClient = sesClient;
        this.senderEmail = senderEmail;
    }

    @Override
    public void send(String to, String subject, String body) {
        sesClient.sendEmail(SendEmailRequest.builder()
                .source(senderEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).build())
                        .body(Body.builder().text(Content.builder().data(body).build()).build())
                        .build())
                .build());
    }
}
