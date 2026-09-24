package com.desyp.notification.subscriber;

import java.util.Base64;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.desyp.common.exception.BusinessException;
import com.desyp.notification.auth.AuthProvider;
import com.desyp.notification.auth.LoginRedirect;
import com.desyp.notification.auth.SocialAccount;
import com.desyp.notification.referral.service.ReferralService;
import com.desyp.notification.subscriber.dto.SubscriberRegisterRequest;
import com.desyp.notification.subscriber.repository.SubscriberRepository;
import com.desyp.notification.subscriber.service.SubscriberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockCookie;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "desyp.admin.naver-account-ids=admin-user")
@AutoConfigureMockMvc
class SubscriberRegistrationTest {

    @Autowired MockMvc mvc;
    @Autowired SubscriberRepository repository;
    @Autowired SubscriberService service;
    @Autowired ReferralService referrals;
    @Autowired SessionRepository<? extends Session> sessions;

    @BeforeEach
    void clean() {
        repository.deleteAllInBatch();
    }

    private RequestPostProcessor naver(String id, String email, String phoneNumber) {
        return oauth2Login().clientRegistration(ClientRegistration.withRegistrationId("naver")
                .clientId("test-client").clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("email", "mobile")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("id").build())
                .attributes(attributes -> {
                    attributes.put("id", id);
                    attributes.put("email", email);
                    if (phoneNumber != null) attributes.put("mobile", phoneNumber);
                });
    }

    private SocialAccount account(String id, String email, String phoneNumber) {
        return new SocialAccount(id, email, phoneNumber);
    }

    private SubscriberRegisterRequest request(String referralCode) {
        return new SubscriberRegisterRequest(true, true, true, referralCode);
    }

    private String body(String referralCode) {
        return """
                {"ageConfirmed":true,"agreePrivacy":true,"agreeMarketing":true,"referralCode":%s}
                """.formatted(referralCode == null ? "null" : "\"" + referralCode + "\"");
    }

    @Test
    void registersNaverProfileAndNormalizesPhoneNumber() throws Exception {
        mvc.perform(post("/api/pre-registrations").with(naver("naver-1", "User@Naver.com", "010-1234-5678"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.referralCode").isNotEmpty())
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phoneNumber").doesNotExist());

        var saved = repository.findAll().getFirst();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.NAVER);
        assertThat(saved.getProviderAccountId()).isEqualTo("naver-1");
        assertThat(saved.getEmailNormalized()).isEqualTo("user@naver.com");
        assertThat(saved.getPhoneNumber()).isEqualTo("01012345678");
        assertThat(saved.getConsentAt()).isNotNull();
    }

    @Test
    void rejectsDuplicateNaverAccount() throws Exception {
        service.register(account("naver-1", "first@naver.com", "010-1111-1111"), request(null));

        mvc.perform(post("/api/pre-registrations").with(naver("naver-1", "second@naver.com", "010-2222-2222"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsSamePhoneAcrossDifferentNaverAccounts() throws Exception {
        service.register(account("naver-1", "first@naver.com", "010-1234-5678"), request(null));

        mvc.perform(post("/api/pre-registrations").with(naver("naver-2", "second@naver.com", "+82 10-1234-5678"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("이미 등록된 휴대전화번호입니다"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void requiresNaverEmailAndPhoneConsent() throws Exception {
        mvc.perform(post("/api/pre-registrations").with(naver("naver-1", "user@naver.com", null))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/pre-registrations").with(naver("naver-2", null, "010-1234-5678"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isBadRequest());
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsGoogleAndUnknownProviders() throws Exception {
        for (RequestPostProcessor login : new RequestPostProcessor[] {oidcLogin(), oauth2Login()}) {
            mvc.perform(post("/api/pre-registrations").with(login).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(body(null)))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsMissingConsent() throws Exception {
        for (String payload : new String[] {
                "{\"ageConfirmed\":false,\"agreePrivacy\":true,\"agreeMarketing\":true}",
                "{\"ageConfirmed\":true,\"agreePrivacy\":false,\"agreeMarketing\":true}",
                "{\"ageConfirmed\":true,\"agreePrivacy\":true,\"agreeMarketing\":false}",
                "{\"ageConfirmed\":true,\"agreePrivacy\":true}"}) {
            mvc.perform(post("/api/pre-registrations").with(naver("user", "user@naver.com", "010-1234-5678"))
                            .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        assertThat(repository.count()).isZero();
    }

    @Test
    void referralChainAwardsBothSides() throws Exception {
        var a = service.register(account("a", "a@naver.com", "010-0000-0001"), request(null));
        var b = service.register(account("b", "b@naver.com", "010-0000-0002"), request(a.referralCode()));
        service.register(account("c", "c@naver.com", "010-0000-0003"), request(b.referralCode()));

        assertThat(referrals.myScore("a").referralCount()).isEqualTo(1);
        assertThat(referrals.myScore("a").referralBonus()).isZero();
        assertThat(referrals.myScore("b").totalScore()).isEqualTo(2);
        assertThat(referrals.myScore("c").referralBonus()).isEqualTo(1);
        mvc.perform(get("/api/referrals/me").with(naver("b", "b@naver.com", "010-0000-0002")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalScore").value(2));
    }

    @Test
    void referralRequiresExistingReferralCodeAndDoesNotAcceptEmail() throws Exception {
        var first = service.register(account("first", "first@naver.com", "010-1111-1111"), request(null));

        mvc.perform(post("/api/pre-registrations").with(naver("other", "other@naver.com", "010-2222-2222"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("missing-code")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
        mvc.perform(post("/api/pre-registrations").with(naver("other", "other@naver.com", "010-2222-2222"))
                        .contentType(MediaType.APPLICATION_JSON).content(body("other@naver.com")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/pre-registrations").with(naver("other", "other@naver.com", "010-2222-2222"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(first.referralCode())))
                .andExpect(status().isCreated());
        assertThat(referrals.myScore("first").referralCount()).isEqualTo(1);
    }

    @Test
    void requiresAuthenticationAndJsonButCsrfOnlyForAdmin() throws Exception {
        mvc.perform(post("/api/pre-registrations").contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/pre-registrations").with(naver("user", "user@naver.com", "010-1234-5678"))
                        .contentType(MediaType.TEXT_PLAIN).content(body(null)))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/pre-registrations").with(naver("user", "user@naver.com", "010-1234-5678"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/admin/mail/event-start").with(naver("admin-user", "admin@naver.com", "010-9999-9999")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/csrf")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void concurrentRegistrationPersistsOnlyOnePhone() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> register = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
            String id = Thread.currentThread().getName();
            try {
                service.register(account(id, id + "@naver.com", "010-5555-5555"), request(null));
                return true;
            } catch (BusinessException exception) {
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
    void rankingIncludesTiesAndRequiresNaverAdmin() throws Exception {
        var a = service.register(account("a", "a@naver.com", "010-0000-0001"), request(null));
        service.register(account("b", "b@naver.com", "010-0000-0002"), request(a.referralCode()));
        service.register(account("c", "c@naver.com", "010-0000-0003"), request(null));

        mvc.perform(get("/api/admin/referrals/ranking?maxRank=1")
                        .with(naver("admin-user", "admin@naver.com", "010-9999-9999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].email").isNotEmpty())
                .andExpect(jsonPath("$.data[0].phoneNumber").doesNotExist());
        mvc.perform(get("/api/admin/referrals/ranking")
                        .with(naver("normal-user", "normal@naver.com", "010-8888-8888")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/referrals/ranking").with(user("admin-user").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSendMailAndInvalidRankingBoundsFail() throws Exception {
        mvc.perform(post("/api/admin/mail/event-start")
                        .with(naver("admin-user", "admin@naver.com", "010-9999-9999")).with(csrf()))
                .andExpect(status().isOk());
        for (String bound : new String[] {"0", "101", "abc"}) {
            mvc.perform(get("/api/admin/referrals/ranking?maxRank=" + bound)
                            .with(naver("admin-user", "admin@naver.com", "010-9999-9999")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void rejectsDuplicateNormalizedEmail() throws Exception {
        service.register(account("first", "foo.bar+tag@gmail.com", "010-1000-0001"), request(null));

        mvc.perform(post("/api/pre-registrations")
                        .with(naver("second", "foobar@googlemail.com", "010-1000-0002"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(null)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownReferralAndDoesNotAwardPointsOnFailedRegistration() {
        assertThatThrownBy(() -> service.register(
                account("unknown", "unknown@naver.com", "010-2000-0001"), request("missing-code")))
                .isInstanceOf(BusinessException.class);

        var a = service.register(account("a", "a@naver.com", "010-2000-0002"), request(null));
        assertThatThrownBy(() -> service.register(
                account("b", "b@naver.com", "010-2000-0003"),
                new SubscriberRegisterRequest(true, true, false, a.referralCode())))
                .isInstanceOf(BusinessException.class);
        assertThat(referrals.myScore("a").totalScore()).isZero();
    }

    @Test
    void duplicateRegistrationCannotChangeReferrerOrAwardBonusTwice() {
        var a = service.register(account("a", "a@naver.com", "010-3000-0001"), request(null));
        var b = service.register(account("b", "b@naver.com", "010-3000-0002"), request(a.referralCode()));
        var c = service.register(account("c", "c@naver.com", "010-3000-0003"), request(null));

        assertThatThrownBy(() -> service.register(
                account("b", "b@naver.com", "010-3000-0002"), request(c.referralCode())))
                .isInstanceOf(BusinessException.class);
        assertThat(referrals.myScore("a").referralCount()).isEqualTo(1);
        assertThat(referrals.myScore("b").referralBonus()).isEqualTo(1);
        assertThat(referrals.myScore("c").totalScore()).isZero();
        assertThat(repository.findById(b.id()).orElseThrow().getReferrer().getId()).isEqualTo(a.id());
    }

    @Test
    void scoreUsesAuthenticatedNaverAccountOnly() throws Exception {
        var a = service.register(account("a", "a@naver.com", "010-4000-0001"), request(null));

        mvc.perform(get("/api/referrals/me?accountId=a")
                        .with(naver("b", "b@naver.com", "010-4000-0002")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/referrals/me?accountId=b")
                        .with(naver("a", "a@naver.com", "010-4000-0001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriberId").value(a.id()));
    }

    @Test
    void concurrentReferralsDoNotLosePoints() throws Exception {
        var a = service.register(account("a", "a@naver.com", "010-5000-0000"), request(null));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int i = 1; i <= 8; i++) {
                int sequence = i;
                tasks.add(executor.submit(() -> {
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                    return service.register(account("invitee-" + sequence, "invitee-" + sequence + "@naver.com",
                            "010-5000-%04d".formatted(sequence)), request(a.referralCode()));
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(referrals.myScore("a").referralCount()).isEqualTo(8);
        assertThat(referrals.myScore("a").totalScore()).isEqualTo(8);
    }

    @Test
    void apiDocsAreAccessibleWithoutLoginAndMalformedJsonFails() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mvc.perform(post("/api/pre-registrations").with(naver("user", "user@naver.com", "010-6000-0001"))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meReturnsMaskedEmailAndRegistrationWithoutPhone() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(naver("me", "desyp@naver.com", "010-7000-0001")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailMasked").value("des***@naver.com"))
                .andExpect(jsonPath("$.registered").value(false))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
        service.register(account("me", "desyp@naver.com", "010-7000-0001"), request(null));
        mvc.perform(get("/api/me").with(naver("me", "desyp@naver.com", "010-7000-0001")))
                .andExpect(jsonPath("$.registered").value(true));
    }

    @Test
    void corsAllowsOnlyFrontendOriginWithCredentials() throws Exception {
        mvc.perform(options("/api/pre-registrations").header("Origin", "https://www.desyp.site")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.desyp.site"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/referrals/me").header("Origin", "https://www.desyp.site")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://www.desyp.site"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/pre-registrations").header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void naverLoginStoresOnlyFrontendReturnToInJdbcSession() throws Exception {
        assertThat(storedReturnTo("https://www.desyp.site/#entry-card")).isEqualTo("https://www.desyp.site/#entry-card");
        for (String unsafe : new String[] {"https://www.desyp.site.evil.com/", "https://evil.com/?x=https://www.desyp.site",
                "//evil.com", "https://user@www.desyp.site/", "javascript:alert(1)"}) {
            assertThat(storedReturnTo(unsafe)).isEqualTo("https://www.desyp.site/");
        }
    }

    private Object storedReturnTo(String returnTo) throws Exception {
        var response = mvc.perform(get("/auth/naver/login").param("return_to", returnTo))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/naver"))
                .andReturn().getResponse();
        var cookie = (MockCookie) response.getCookie("SESSION");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
        assertThat(cookie.getSecure()).isTrue();
        String sessionId = new String(Base64.getDecoder().decode(cookie.getValue()));
        Session session = sessions.findById(sessionId);
        assertThat(session).isNotNull();
        return session.getAttribute(LoginRedirect.class.getName() + ".RETURN_TO");
    }
}
