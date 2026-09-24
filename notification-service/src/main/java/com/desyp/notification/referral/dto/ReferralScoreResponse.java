package com.desyp.notification.referral.dto;

public record ReferralScoreResponse(Long subscriberId, String referralCode,
                                    long referralCount, int referralBonus, long totalScore) {
}
