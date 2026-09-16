package com.desyp.notification.subscriber;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.service.SubscriberService;
import com.desyp.notification.subscriber.util.EmailNormalizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "desyp.admin.google-subs=admin-user")
@AutoConfigureMockMvc
class SubscriberRegistrationTest {
    @Autowired MockMvc mvc;
    @Autowired SubscriberRepository repository;
    @Autowired SubscriberService service;
    @Autowired com.desyp.notification.referral.service.ReferralService referrals;

    @BeforeEach
    void clean() {
        repository.deleteAllInBatch();
    }

    private RequestPostProcessor google(String subject, String email, boolean verified) {
        return oidcLogin().clientRegistration(ClientRegistration.withRegistrationId("google")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userNameAttributeName("sub").build())
                .idToken(token -> token.subject(subject).claim("email", email).claim("email_verified", verified));
    }

    private RequestPostProcessor naver(String id, String email) {
        return oauth2Login().clientRegistration(ClientRegistration.withRegistrationId("naver")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("email")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("id").build())
                .attributes(attrs -> {
                    attrs.put("id", id);
                    attrs.put("email", email);
                });
    }

    private String body(String email, String referral) {
        return """
                {"email":"%s","ageConfirmed":true,"privacyConsented":true,"referralCode":%s}
                """.formatted(email, referral == null ? "null" : "\"" + referral + "\"");
    }

    private SubscriberRegisterRequest request(String email, String referral) {
        return new SubscriberRegisterRequest(email, true, true, referral);
    }

    @Test
    void registersAndPersistsConsentAndNormalizedEmail() throws Exception {
        mvc.perform(post("/api/subscribers").with(google("user-1", "Foo.Bar+tag@gmail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("Foo.Bar+tag@gmail.com", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.inviteToken").isNotEmpty())
                .andExpect(jsonPath("$.data.email").doesNotExist());
        var saved = repository.findAll().getFirst();
        assertThat(saved.getEmailNormalized()).isEqualTo("foobar@gmail.com");
        assertThat(saved.getConsentAt()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.isAgeConfirmed()).isTrue();
    }

    @Test
    void duplicateGoogleAccountReturnsConflict() throws Exception {
        service.register(AuthProvider.GOOGLE, "user-1", "first@gmail.com", request("first@gmail.com", null));
        mvc.perform(post("/api/subscribers").with(google("user-1", "second@gmail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("second@gmail.com", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void registersViaNaverLogin() throws Exception {
        mvc.perform(post("/api/subscribers").with(naver("naver-1", "user@naver.com"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("user@naver.com", null)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true));
        var saved = repository.findAll().getFirst();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.NAVER);
        assertThat(saved.getProviderAccountId()).isEqualTo("naver-1");
    }

    @Test
    void sameRawIdDoesNotCollideAcrossProviders() throws Exception {
        service.register(AuthProvider.GOOGLE, "same-id", "google-user@gmail.com", request("google-user@gmail.com", null));
        mvc.perform(post("/api/subscribers").with(naver("same-id", "naver-user@naver.com"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("naver-user@naver.com", null)))
                .andExpect(status().isCreated());
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void duplicateNaverAccountReturnsConflict() throws Exception {
        service.register(AuthProvider.NAVER, "naver-1", "first@naver.com", request("first@naver.com", null));
        mvc.perform(post("/api/subscribers").with(naver("naver-1", "second@naver.com"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("second@naver.com", null)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void emailAliasesCannotRegisterTwice() throws Exception {
        service.register(AuthProvider.GOOGLE, "user-1", "foo.bar@gmail.com", request("foo.bar@gmail.com", null));
        mvc.perform(post("/api/subscribers").with(google("user-2", "foobar+tag@googlemail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("foobar+tag@googlemail.com", null)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void registersWithExistingReferrer() {
        var first = service.register(AuthProvider.GOOGLE, "first", "first@gmail.com", request("first@gmail.com", null));
        var second = service.register(AuthProvider.GOOGLE, "second", "second@gmail.com", request("second@gmail.com", first.inviteToken()));
        assertThat(repository.findById(second.id()).orElseThrow().getReferrer().getId()).isEqualTo(first.id());
    }

    @Test
    void rejectsUnknownReferral() throws Exception {
        mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("user@gmail.com", "unknown")))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsSelfReferral() {
        var first = service.register(AuthProvider.GOOGLE, "first", "first@gmail.com", request("first@gmail.com", null));
        assertThatThrownBy(() -> service.register(AuthProvider.GOOGLE, "first", "first@gmail.com", request("first@gmail.com", first.inviteToken())))
                .isInstanceOf(BusinessException.class).hasMessage("자기 자신을 추천할 수 없습니다");
    }

    @Test
    void rejectsMissingOrFalseConsent() throws Exception {
        for (String payload : new String[] {
                "{\"email\":\"user@gmail.com\",\"ageConfirmed\":false,\"privacyConsented\":true}",
                "{\"email\":\"user@gmail.com\",\"ageConfirmed\":true,\"privacyConsented\":false}",
                "{\"email\":\"user@gmail.com\",\"ageConfirmed\":true}"}) {
            mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", true))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void domainAlsoRequiresConsent() {
        assertThatThrownBy(() -> service.register(AuthProvider.GOOGLE, "user", "user@gmail.com",
                new SubscriberRegisterRequest("user@gmail.com", true, false, null)))
                .isInstanceOf(BusinessException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsUnverifiedOrMismatchedEmail() throws Exception {
        mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", false))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("user@gmail.com", null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("other@gmail.com", null)))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsInvalidEmailAndMalformedJson() throws Exception {
        for (String payload : new String[] {body("bad-email", null), "{"}) {
            mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", true))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void apiDocsAreAccessibleWithoutLogin() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/subscribers").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(body("user@gmail.com", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOtherOidcProvider() throws Exception {
        mvc.perform(post("/api/subscribers").with(oidcLogin()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body("user@gmail.com", null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfAndExposesToken() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
        mvc.perform(post("/api/subscribers").with(google("user", "user@gmail.com", true))
                        .contentType(MediaType.APPLICATION_JSON).content(body("user@gmail.com", null)))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    void concurrentRegistrationPersistsOnlyOneRow() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> register = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            try {
                service.register(AuthProvider.GOOGLE, "same-user", "same@gmail.com", request("same@gmail.com", null));
                return true;
            } catch (BusinessException exception) {
                assertThat(exception.getErrorCode().getHttpStatus().value()).isEqualTo(409);
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(register);
            var two = executor.submit(register);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void normalizationDoesNotDependOnMachineLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(EmailNormalizer.normalize("INFO@GMAIL.COM")).isEqualTo("info@gmail.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void referralChainAwardsBothSidesAndKeepsCountsSeparate() throws Exception {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        var b = service.register(AuthProvider.GOOGLE, "b", "b@gmail.com", request("b@gmail.com", a.inviteToken()));
        service.register(AuthProvider.GOOGLE, "c", "c@gmail.com", request("c@gmail.com", b.inviteToken()));
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").referralCount()).isEqualTo(1);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").referralBonus()).isZero();
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "b").referralCount()).isEqualTo(1);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "b").referralBonus()).isEqualTo(1);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "b").totalScore()).isEqualTo(2);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "c").referralCount()).isZero();
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "c").referralBonus()).isEqualTo(1);
        mvc.perform(get("/api/referrals/me").with(google("b", "b@gmail.com", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralCount").value(1))
                .andExpect(jsonPath("$.data.referralBonus").value(1))
                .andExpect(jsonPath("$.data.totalScore").value(2))
                .andExpect(jsonPath("$.data.inviteToken").value(b.inviteToken()));
    }

    @Test
    void duplicateRegistrationCannotAwardBonusTwiceOrChangeReferrer() {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        var b = service.register(AuthProvider.GOOGLE, "b", "b@gmail.com", request("b@gmail.com", a.inviteToken()));
        var c = service.register(AuthProvider.GOOGLE, "c", "c@gmail.com", request("c@gmail.com", null));
        assertThatThrownBy(() -> service.register(AuthProvider.GOOGLE, "b", "b@gmail.com", request("b@gmail.com", c.inviteToken())))
                .isInstanceOf(BusinessException.class);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").referralCount()).isEqualTo(1);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "b").referralBonus()).isEqualTo(1);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "c").totalScore()).isZero();
        assertThat(repository.findById(b.id()).orElseThrow().getReferrer().getId()).isEqualTo(a.id());
    }

    @Test
    void unsuccessfulRegistrationDoesNotAwardPoints() {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        assertThatThrownBy(() -> service.register(AuthProvider.GOOGLE, "b", "b@gmail.com",
                new SubscriberRegisterRequest("b@gmail.com", true, false, a.inviteToken())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.register(AuthProvider.GOOGLE, "b", "other@gmail.com", request("b@gmail.com", a.inviteToken())))
                .isInstanceOf(BusinessException.class);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").totalScore()).isZero();
    }

    @Test
    void rankingIncludesAllTiesAtRequestedRankAndDoesNotExposeInviteTokens() throws Exception {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        service.register(AuthProvider.GOOGLE, "b", "b@gmail.com", request("b@gmail.com", a.inviteToken()));
        service.register(AuthProvider.GOOGLE, "c", "c@gmail.com", request("c@gmail.com", null));
        mvc.perform(get("/api/admin/referrals/ranking?maxRank=1").with(google("admin-user", "admin@gmail.com", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].rank").value(1))
                .andExpect(jsonPath("$.data[0].totalScore").value(1))
                .andExpect(jsonPath("$.data[0].inviteToken").doesNotExist());
        assertThat(referrals.ranking(10)).extracting(row -> row.rank()).containsExactly(1L, 1L, 3L);
    }

    @Test
    void rankingRequiresVerifiedAllowlistedGoogleAccount() throws Exception {
        mvc.perform(get("/api/admin/referrals/ranking")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/referrals/ranking").with(google("normal-user", "normal@gmail.com", true)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/referrals/ranking").with(google("admin-user", "admin@gmail.com", false)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/referrals/ranking").with(user("admin-user").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void mailEventStartRequiresAdmin() throws Exception {
        mvc.perform(post("/api/admin/mail/event-start").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/mail/event-start").with(google("normal-user", "normal@gmail.com", true)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/mail/event-start").with(google("admin-user", "admin@gmail.com", true)).with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void rankingRejectsInvalidBounds() throws Exception {
        for (String bound : new String[] {"0", "101", "abc"}) {
            mvc.perform(get("/api/admin/referrals/ranking?maxRank=" + bound)
                            .with(google("admin-user", "admin@gmail.com", true)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
        }
    }

    @Test
    void scoreRequiresRegisteredAccountAndDoesNotAcceptAnotherUserId() throws Exception {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        mvc.perform(get("/api/referrals/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/referrals/me?googleSub=a").with(google("b", "b@gmail.com", true)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/referrals/me?googleSub=b").with(google("a", "a@gmail.com", true)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.subscriberId").value(a.id()));
    }

    @Test
    void concurrentReferralsDoNotLosePoints() throws Exception {
        var a = service.register(AuthProvider.GOOGLE, "a", "a@gmail.com", request("a@gmail.com", null));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 0; i < 8; i++) {
                String subject = "invitee-" + i;
                tasks.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return service.register(AuthProvider.GOOGLE, subject, subject + "@gmail.com", request(subject + "@gmail.com", a.inviteToken()));
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").referralCount()).isEqualTo(8);
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").referralBonus()).isZero();
        assertThat(referrals.myScore(AuthProvider.GOOGLE, "a").totalScore()).isEqualTo(8);
        for (int i = 0; i < 8; i++) assertThat(referrals.myScore(AuthProvider.GOOGLE, "invitee-" + i).referralBonus()).isEqualTo(1);
    }
}
