package com.desyp.notification.referral.dto;

public record ReferralScoreResponse(Long subscriberId, String inviteToken,
                                    long referralCount, int referralBonus, long totalScore) {
}
