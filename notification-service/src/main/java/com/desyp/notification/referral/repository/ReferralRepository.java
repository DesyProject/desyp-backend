package com.desyp.notification.referral.repository;

import java.util.List;
import java.util.Optional;
import com.desyp.notification.subscriber.entity.Subscriber;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface ReferralRepository extends Repository<Subscriber, Long> {

    @Query(value = """
            SELECT s.id AS subscriberId, s.invite_token AS inviteToken,
                   COUNT(c.id) AS referralCount, s.referral_bonus AS referralBonus,
                   COUNT(c.id) + s.referral_bonus AS totalScore
            FROM subscribers s LEFT JOIN subscribers c ON c.referrer_id = s.id
            WHERE s.provider = :provider AND s.provider_account_id = :providerAccountId
            GROUP BY s.id, s.invite_token, s.referral_bonus
            """, nativeQuery = true)
    Optional<Score> findScore(@Param("provider") String provider, @Param("providerAccountId") String providerAccountId);

    @Query(value = """
            SELECT ranked.* FROM (
                SELECT RANK() OVER (ORDER BY COUNT(c.id) + s.referral_bonus DESC) AS ranking,
                       s.id AS subscriberId, s.email AS email,
                       COUNT(c.id) AS referralCount, s.referral_bonus AS referralBonus,
                       COUNT(c.id) + s.referral_bonus AS totalScore
                FROM subscribers s LEFT JOIN subscribers c ON c.referrer_id = s.id
                GROUP BY s.id, s.email, s.referral_bonus
            ) ranked
            WHERE ranked.ranking <= :maxRank
            ORDER BY ranked.ranking, ranked.subscriberId
            """, nativeQuery = true)
    List<Ranking> findRanking(@Param("maxRank") int maxRank);

    interface Score {
        Long getSubscriberId();
        String getInviteToken();
        long getReferralCount();
        int getReferralBonus();
        long getTotalScore();
    }

    interface Ranking {
        long getRanking();
        Long getSubscriberId();
        String getEmail();
        long getReferralCount();
        int getReferralBonus();
        long getTotalScore();
    }
}
