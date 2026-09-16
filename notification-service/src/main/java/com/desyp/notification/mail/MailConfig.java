package com.desyp.notification.mail;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import software.amazon.awssdk.services.ses.SesClient;

@Configuration
@Profile("!test")
public class MailConfig {

    @Bean
    SesClient sesClient() {
        return SesClient.builder().build();
    }
}
