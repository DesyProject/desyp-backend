package com.desyp.notification.subscriber.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriberRegisterRequest(
        @NotNull @AssertTrue Boolean ageConfirmed,
        @NotNull @AssertTrue Boolean agreePrivacy,
        @NotNull @AssertTrue Boolean agreeMarketing,
        @Size(max = 64) String referralCode
) {
}
