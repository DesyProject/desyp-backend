package com.desyp.notification.admin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.desyp.notification.conversion.KakaoChannelClickRepository;
import com.desyp.notification.referral.repository.ReferralRepository;
import com.desyp.notification.subscriber.repository.SubscriberRepository;

@Service
@Transactional(readOnly = true)
public class AdminStatsService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int TOP_REFERRER_RANK = 10;

    private final SubscriberRepository subscriberRepository;
    private final KakaoChannelClickRepository kakaoChannelClickRepository;
    private final ReferralRepository referralRepository;
    private final OffsetDateTime registrationEndAt;

    public AdminStatsService(SubscriberRepository subscriberRepository,
            KakaoChannelClickRepository kakaoChannelClickRepository, ReferralRepository referralRepository,
            @Value("${desyp.registration.end-at}") OffsetDateTime registrationEndAt) {
        this.subscriberRepository = subscriberRepository;
        this.kakaoChannelClickRepository = kakaoChannelClickRepository;
        this.referralRepository = referralRepository;
        this.registrationEndAt = registrationEndAt;
    }

    public AdminStatsResponse stats(int days) {
        var summary = new AdminStatsResponse.Summary(subscriberRepository.count(),
                subscriberRepository.countByReferrerIsNotNull(), kakaoChannelClickRepository.count(),
                subscriberRepository.countByMarketingAgreedTrue(), subscriberRepository.countByNotifiedAtIsNotNull());
        var sources = subscriberRepository.countByUtmSource().stream()
                .map(s -> new AdminStatsResponse.Source(s.getSource(), s.getCount()))
                .toList();
        var topReferrers = referralRepository.findRanking(TOP_REFERRER_RANK).stream()
                .map(r -> new AdminStatsResponse.TopReferrer(r.getRanking(), r.getSubscriberId(),
                        r.getEmail(), r.getReferralCount(), r.getReferralBonus(),
                        r.getTotalScore()))
                .toList();
        return new AdminStatsResponse(OffsetDateTime.now(ZoneOffset.UTC), days, registrationEndAt,
                summary, daily(days), sources, topReferrers);
    }

    // ponytail: 기간 내 행을 모두 읽어 한국 날짜로 묶는다. 등록자가 수만 명을 넘으면 SQL GROUP BY로 바꾼다.
    private List<AdminStatsResponse.Daily> daily(int days) {
        LocalDate first = LocalDate.now(KST).minusDays(days - 1L);
        OffsetDateTime from = first.atStartOfDay(KST).toOffsetDateTime();
        long[] registrations = new long[days];
        long[] referred = new long[days];
        long[] clicks = new long[days];
        for (var row : subscriberRepository.findByCreatedAtGreaterThanEqual(from)) {
            int i = index(first, row.getCreatedAt(), days);
            if (i < 0) continue;
            registrations[i]++;
            referred[i] += row.getReferralBonus();
        }
        for (var click : kakaoChannelClickRepository.findByClickedAtGreaterThanEqual(from)) {
            int i = index(first, click.getClickedAt(), days);
            if (i >= 0) clicks[i]++;
        }
        var result = new ArrayList<AdminStatsResponse.Daily>(days);
        for (int i = 0; i < days; i++) {
            result.add(new AdminStatsResponse.Daily(first.plusDays(i), registrations[i], referred[i], clicks[i]));
        }
        return result;
    }

    private static int index(LocalDate first, OffsetDateTime at, int days) {
        long i = at.atZoneSameInstant(KST).toLocalDate().toEpochDay() - first.toEpochDay();
        return i >= 0 && i < days ? (int) i : -1;
    }
}
