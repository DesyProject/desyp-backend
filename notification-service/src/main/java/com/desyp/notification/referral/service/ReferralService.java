package com.desyp.notification.referral.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.referral.dto.ReferralScoreResponse;
import com.desyp.notification.referral.dto.ReferralRankingResponse;
import com.desyp.notification.referral.repository.ReferralRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.desyp.notification.subscriber.exception.SubscriberErrorCode.SUBSCRIBER_NOT_FOUND;

@Service
@Transactional(readOnly = true)
public class ReferralService {
    private final ReferralRepository referralRepository;
    private final String drawSeed;

    public ReferralService(ReferralRepository referralRepository,
            @Value("${desyp.registration.end-at}") OffsetDateTime registrationEndAt) {
        this.referralRepository = referralRepository;
        this.drawSeed = registrationEndAt.toString();
    }

    public ReferralScoreResponse myScore(String providerAccountId) {
        var score = referralRepository.findScore(AuthProvider.NAVER.name(), providerAccountId)
                .orElseThrow(() -> new BusinessException(SUBSCRIBER_NOT_FOUND));
        return new ReferralScoreResponse(score.getSubscriberId(), score.getReferralCode(),
                score.getReferralCount(), score.getReferralBonus(), score.getTotalScore());
    }

    /**
     * rank는 1, 1, 3 공동 순위이고, drawRank는 공동 순위 안을 무작위로 정한 최종 순위다.
     * 무작위 순서는 등록자 ID와 마감 시각의 해시로 정해 새로고침해도 바뀌지 않고 누구나 다시 계산할 수 있다.
     */
    // ponytail: 마감 시각을 시드로 쓰는 결정적 추첨. 추첨 시점·결과를 기록해야 하면 결과 스냅샷 테이블로 바꾼다.
    public List<ReferralRankingResponse> ranking(int maxRank) {
        var rows = referralRepository.findRanking(maxRank).stream()
                .sorted(Comparator.comparingLong(ReferralRepository.Ranking::getRanking)
                        .thenComparingLong(row -> drawKey(row.getSubscriberId())))
                .toList();
        return IntStream.range(0, rows.size())
                .mapToObj(i -> {
                    var row = rows.get(i);
                    return new ReferralRankingResponse(row.getRanking(), i + 1L, row.getSubscriberId(),
                            row.getEmail(), row.getReferralCount(), row.getReferralBonus(), row.getTotalScore());
                })
                .toList();
    }

    private long drawKey(Long subscriberId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((drawSeed + ":" + subscriberId).getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // 모든 JVM에 SHA-256이 있다
        }
    }
}
