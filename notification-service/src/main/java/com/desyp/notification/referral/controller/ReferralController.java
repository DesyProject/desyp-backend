package com.desyp.notification.referral.controller;

import java.util.List;
import com.desyp.common.response.ApiResponse;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.referral.dto.ReferralScoreResponse;
import com.desyp.notification.referral.dto.ReferralRankingResponse;
import com.desyp.notification.referral.service.ReferralService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class ReferralController {
    private final ReferralService referralService;

    @GetMapping("/api/referrals/me")
    public ApiResponse<ReferralScoreResponse> myScore(OAuth2AuthenticationToken authentication) {
        var account = SocialAccount.require(authentication);
        return ApiResponse.success(referralService.myScore(account.provider(), account.accountId()));
    }

    @GetMapping("/api/admin/referrals/ranking")
    public ApiResponse<List<ReferralRankingResponse>> ranking(
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int maxRank) {
        return ApiResponse.success(referralService.ranking(maxRank));
    }
}
