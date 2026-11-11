package com.desyp.notification.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record AdminStatsResponse(OffsetDateTime generatedAt, int days, OffsetDateTime registrationEndAt,
                                 Summary summary, List<Daily> daily, List<TopReferrer> topReferrers) {

    public record Summary(long subscribers, long referred, long kakaoClicks, long mailTargets, long mailSent) {
    }

    public record Daily(LocalDate date, long registrations, long referred, long kakaoClicks) {
    }

    public record TopReferrer(long rank, Long subscriberId, String email, long referralCount,
                              int referralBonus, long totalScore) {
    }
}
