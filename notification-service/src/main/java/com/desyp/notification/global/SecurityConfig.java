package com.desyp.notification.global;

import jakarta.servlet.http.HttpServletResponse;
import com.desyp.notification.auth.AdminAccess;
import com.desyp.notification.auth.NaverOAuth2UserService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations, AdminAccess adminAccess,
            NaverOAuth2UserService naverOAuth2UserService) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/csrf", "/oauth2/**", "/login/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/admin/**").access((authentication, context) ->
                        new AuthorizationDecision(adminAccess.isAllowed(authentication.get())))
                .anyRequest().authenticated());
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"로그인이 필요합니다\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"요청 권한 또는 CSRF 토큰을 확인해주세요\"}");
                }));
        if (registrations.getIfAvailable() != null) {
            http.oauth2Login(login -> login.defaultSuccessUrl("/api/csrf", true)
                    .userInfoEndpoint(userInfo -> userInfo.userService(naverOAuth2UserService)));
        }
        http.csrf(Customizer.withDefaults());
        return http.build();
    }
}
