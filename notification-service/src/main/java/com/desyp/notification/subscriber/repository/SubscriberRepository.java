package com.desyp.notification.subscriber.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.subscriber.entity.Subscriber;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    boolean existsByProviderAndProviderAccountId(AuthProvider provider, String providerAccountId);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<Subscriber> findByProviderAndProviderAccountId(AuthProvider provider, String providerAccountId);

    Optional<Subscriber> findByReferralCode(String referralCode);

    boolean existsByReferralCode(String referralCode);

    @Query("select s.id from Subscriber s where s.notifiedAt is null and s.marketingAgreed = true order by s.id")
    List<Long> findPendingNotificationIds();

    long countByReferrerIsNotNull();

    long countByMarketingAgreedTrue();

    long countByNotifiedAtIsNotNull();

    List<RegistrationTime> findByCreatedAtGreaterThanEqual(OffsetDateTime from);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Subscriber s where s.id = :id")
    Optional<Subscriber> findByIdForUpdate(Long id);

    interface RegistrationTime {
        OffsetDateTime getCreatedAt();
        int getReferralBonus();
    }
}
