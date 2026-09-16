package com.desip.notification.subscriber.util;

import java.util.Set;
import java.util.Locale;

public final class EmailNormalizer {

    private static final Set<String> GMAIL_ALIAS_DOMAINS = Set.of("gmail.com", "googlemail.com");

    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        String lower = email.trim().toLowerCase(Locale.ROOT);
        int atIndex = lower.indexOf('@');
        if (atIndex < 0) {
            return lower;
        }

        String local = lower.substring(0, atIndex);
        String domain = lower.substring(atIndex + 1);

        int plusIndex = local.indexOf('+');
        if (plusIndex >= 0) {
            local = local.substring(0, plusIndex);
        }

        if (GMAIL_ALIAS_DOMAINS.contains(domain)) {
            local = local.replace(".", "");
            domain = "gmail.com";
        }

        return local + "@" + domain;
    }
}
