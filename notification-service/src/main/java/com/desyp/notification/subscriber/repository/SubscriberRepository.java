package com.desyp.notification.subscriber.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.subscriber.entity.Subscriber;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    boolean existsByProviderAndProviderAccountId(AuthProvider provider, String providerAccountId);

    boolean existsByEmailNormalized(String emailNormalized);

    boolean existsByPhoneNumber(String phoneNumber);

    Optional<Subscriber> findByEmailNormalized(String emailNormalized);

    List<Subscriber> findByNotifiedAtIsNullAndMarketingAgreedTrue();
}
