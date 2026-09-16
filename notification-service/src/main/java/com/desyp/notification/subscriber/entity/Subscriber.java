package com.desyp.notification.subscriber.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.desyp.notification.auth.AuthProvider;

import lombok.Builder;
import lombok.Getter;

@Entity
@Table(name = "subscribers")
@Getter
public class Subscriber {

    protected Subscriber() {
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuthProvider provider;

    @Column(name = "provider_account_id", nullable = false, updatable = false)
    private String providerAccountId;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_normalized", nullable = false, unique = true)
    private String emailNormalized;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", updatable = false)
    private Subscriber referrer;

    @Column(name = "referral_bonus", nullable = false, updatable = false)
    private int referralBonus;

    @Column(name = "invite_token", nullable = false, unique = true)
    private String inviteToken;

    @Column(name = "age_confirmed", nullable = false)
    private boolean ageConfirmed;

    @Column(name = "consent_at", nullable = false)
    private OffsetDateTime consentAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private Subscriber(AuthProvider provider, String providerAccountId, String email, String emailNormalized,
                        Subscriber referrer, String inviteToken, boolean ageConfirmed, OffsetDateTime consentAt) {
        this.provider = provider;
        this.providerAccountId = providerAccountId;
        this.email = email;
        this.emailNormalized = emailNormalized;
        this.referrer = referrer;
        this.referralBonus = referrer == null ? 0 : 1;
        this.inviteToken = inviteToken;
        this.ageConfirmed = ageConfirmed;
        this.consentAt = consentAt;
    }
}
