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

    /** 화면 공유를 고려해 이메일은 마스킹한다. 연락용 원문은 /api/admin/referrals/ranking에서 조회한다. */
    public record TopReferrer(long rank, Long subscriberId, String emailMasked, long referralCount,
                              int referralBonus, long totalScore) {
    }
}
