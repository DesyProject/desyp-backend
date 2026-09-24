package com.desyp.notification.subscriber.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailNormalizerTest {

    @Test
    void gmail_별칭을_우회해도_동일하게_정규화된다() {
        assertThat(EmailNormalizer.normalize("Foo.Bar+spam@Gmail.com"))
                .isEqualTo(EmailNormalizer.normalize("foobar@googlemail.com"))
                .isEqualTo("foobar@gmail.com");
    }

    @Test
    void gmail이_아닌_도메인은_플러스가_주소의_일부이므로_보존한다() {
        assertThat(EmailNormalizer.normalize("a.b+tag@example.com"))
                .isEqualTo("a.b+tag@example.com");
    }
}
