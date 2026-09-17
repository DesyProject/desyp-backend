package com.desyp.notification.subscriber.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriberRegisterRequest(
        @NotNull @AssertTrue Boolean ageConfirmed,
        @NotNull @AssertTrue Boolean privacyConsented,
        @Size(max = 64) String referralCode
) {
}
