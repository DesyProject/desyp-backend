package com.desyp.notification.referral.service;

import java.util.List;
import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.referral.dto.ReferralScoreResponse;
import com.desyp.notification.referral.dto.ReferralRankingResponse;
import com.desyp.notification.referral.repository.ReferralRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SUBSCRIBER_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralService {
    private final ReferralRepository referralRepository;

    public ReferralScoreResponse myScore(String providerAccountId) {
        var score = referralRepository.findScore(AuthProvider.NAVER.name(), providerAccountId)
                .orElseThrow(() -> new BusinessException(SUBSCRIBER_NOT_FOUND));
        return new ReferralScoreResponse(score.getSubscriberId(), score.getReferralCode(),
                score.getReferralCount(), score.getReferralBonus(), score.getTotalScore());
    }

    public List<ReferralRankingResponse> ranking(int maxRank) {
        return referralRepository.findRanking(maxRank).stream()
                .map(score -> new ReferralRankingResponse(score.getRanking(), score.getSubscriberId(),
                        score.getEmail(), score.getReferralCount(),
                        score.getReferralBonus(), score.getTotalScore()))
                .toList();
    }
}
