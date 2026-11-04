package com.desyp.notification.subscriber.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void normalizesGmailAliasesToSameAddress() {
        assertThat(EmailNormalizer.normalize("Foo.Bar+spam@Gmail.com"))
                .isEqualTo(EmailNormalizer.normalize("foobar@googlemail.com"))
                .isEqualTo("foobar@gmail.com");
    }

    @Test
    void keepsPlusSuffixForNonGmailDomains() {
        assertThat(EmailNormalizer.normalize("a.b+tag@example.com"))
                .isEqualTo("a.b+tag@example.com");
    }
}
