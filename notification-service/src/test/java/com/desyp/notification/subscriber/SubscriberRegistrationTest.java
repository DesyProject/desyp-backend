package com.desyp.notification.subscriber;

import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.desyp.common.exception.BusinessException;
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

@SpringBootTest
@AutoConfigureMockMvc
class SubscriberRegistrationTest {
    @Autowired MockMvc mvc;
    @Autowired SubscriberRepository repository;
    @Autowired SubscriberService service;

    @BeforeEach
    void clean() {
        repository.deleteAll();
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
        service.register("user-1", "first@gmail.com", request("first@gmail.com", null));
        mvc.perform(post("/api/subscribers").with(google("user-1", "second@gmail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("second@gmail.com", null)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void emailAliasesCannotRegisterTwice() throws Exception {
        service.register("user-1", "foo.bar@gmail.com", request("foo.bar@gmail.com", null));
        mvc.perform(post("/api/subscribers").with(google("user-2", "foobar+tag@googlemail.com", true))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("foobar+tag@googlemail.com", null)))
                .andExpect(status().isConflict());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void registersWithExistingReferrer() {
        var first = service.register("first", "first@gmail.com", request("first@gmail.com", null));
        var second = service.register("second", "second@gmail.com", request("second@gmail.com", first.inviteToken()));
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
        var first = service.register("first", "first@gmail.com", request("first@gmail.com", null));
        assertThatThrownBy(() -> service.register("first", "first@gmail.com", request("first@gmail.com", first.inviteToken())))
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
        assertThatThrownBy(() -> service.register("user", "user@gmail.com",
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
                service.register("same-user", "same@gmail.com", request("same@gmail.com", null));
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
}
