package com.desyp.notification.subscriber.util;

import com.desyp.common.exception.BusinessException;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.VERIFIED_PHONE_REQUIRED;

public final class PhoneNumberNormalizer {

    private PhoneNumberNormalizer() {
    }

    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            throw new BusinessException(VERIFIED_PHONE_REQUIRED);
        }
        String digits = phoneNumber.replaceAll("[^0-9]", "");
        if (digits.startsWith("82")) {
            digits = "0" + digits.substring(2);
        }
        if (!digits.matches("01[016789][0-9]{7,8}")) {
            throw new BusinessException(VERIFIED_PHONE_REQUIRED);
        }
        return digits;
    }
}
