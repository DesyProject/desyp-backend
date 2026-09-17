package com.desyp.notification.referral.dto;

public record ReferralRankingResponse(long rank, Long subscriberId, String email, String phoneNumber,
                                      long referralCount, int referralBonus, long totalScore) {
}
