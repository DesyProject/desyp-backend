package com.desyp.notification.subscriber.util;

import com.desyp.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PhoneNumberNormalizerTest {

    @Test
    void normalizesDomesticAndCountryCodeFormats() {
        assertThat(PhoneNumberNormalizer.normalize("010-1234-5678")).isEqualTo("01012345678");
        assertThat(PhoneNumberNormalizer.normalize("+82 10 1234 5678")).isEqualTo("01012345678");
    }

    @Test
    void rejectsMissingOrInvalidMobileNumber() {
        for (String value : new String[] {"", "02-1234-5678", "010-123-456"}) {
            assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(value))
                    .isInstanceOf(BusinessException.class);
        }
        assertThatThrownBy(() -> PhoneNumberNormalizer.normalize(null))
                .isInstanceOf(BusinessException.class);
    }
}
