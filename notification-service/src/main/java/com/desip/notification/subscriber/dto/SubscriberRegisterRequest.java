package com.desip.notification.subscriber.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SubscriberRegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @AssertTrue Boolean ageConfirmed,
        @NotNull @AssertTrue Boolean privacyConsented,
        @Size(max = 64) String referralCode
) {
}
