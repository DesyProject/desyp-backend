package com.desyp.notification.referral.dto;

/** rank: 공동 순위(1, 1, 3). drawRank: 공동 순위 안을 추첨해 정한 최종 순위(1, 2, 3). */
public record ReferralRankingResponse(long rank, long drawRank, Long subscriberId, String email,
                                      long referralCount, int referralBonus, long totalScore) {
}
