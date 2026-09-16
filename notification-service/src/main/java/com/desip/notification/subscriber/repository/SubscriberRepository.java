package com.desip.notification.subscriber.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.desip.notification.subscriber.entity.Subscriber;

public interface SubscriberRepository extends JpaRepository<Subscriber, Long> {

    boolean existsByGoogleSub(String googleSub);

    boolean existsByEmailNormalized(String emailNormalized);

    Optional<Subscriber> findByInviteToken(String inviteToken);
}
