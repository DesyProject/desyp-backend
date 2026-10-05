package com.desyp.notification.conversion;

import jakarta.validation.constraints.NotNull;

public record KakaoChannelClickRequest(@NotNull ConversionSource source) {
}
