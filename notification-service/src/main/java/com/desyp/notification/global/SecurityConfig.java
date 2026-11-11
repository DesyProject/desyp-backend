package com.desyp.notification.global;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import com.desyp.notification.auth.AdminAccess;
import com.desyp.notification.auth.LoginRedirect;
import com.desyp.notification.auth.NaverOAuth2UserService;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /**
     * 관리자 대시보드는 브라우저 기본 로그인 창(HTTP Basic)으로 아이디·비밀번호를 받는다.
     * 계정은 환경 변수로만 받고, 비어 있으면 모든 요청을 거부한다. 세션을 만들지 않는다.
     */
    @Bean
    @Order(1)
    SecurityFilterChain adminDashboardFilterChain(HttpSecurity http,
            @Value("${desyp.admin.dashboard.username:}") String username,
            @Value("${desyp.admin.dashboard.password:}") String password) throws Exception {
        var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
        var users = new ArrayList<UserDetails>();
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            users.add(User.withUsername(username).password(encoder.encode(password)).roles("DASHBOARD").build());
        }
        http.securityMatcher("/admin.html", "/api/admin/stats")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("DASHBOARD"))
                .httpBasic(basic -> basic.realmName("desyp admin"))
                .userDetailsService(new InMemoryUserDetailsManager(users))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            ObjectProvider<ClientRegistrationRepository> registrations, AdminAccess adminAccess,
            NaverOAuth2UserService naverOAuth2UserService, LoginRedirect loginRedirect) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/api/csrf", "/auth/**", "/oauth2/**",
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
            http.oauth2Login(login -> login
                    .redirectionEndpoint(redirection -> redirection.baseUri("/auth/*/callback"))
                    .successHandler(loginRedirect).failureHandler(loginRedirect)
                    .userInfoEndpoint(userInfo -> userInfo.userService(naverOAuth2UserService)));
        }
        // 프런트는 CSRF 토큰을 받지 않는다. 사전 등록과 전환 기록은 SameSite=Lax 세션 쿠키, JSON 전용 요청,
        // 프런트 Origin만 허용하는 CORS 사전 요청으로 교차 사이트 요청을 막는다. 관리자 API는 토큰을 유지한다.
        http.csrf(csrf -> csrf.ignoringRequestMatchers("/api/pre-registrations", "/api/conversions/kakao-channel-click"));
        http.cors(Customizer.withDefaults());
        return http.build();
    }

    /**
     * Spring Boot는 내장 웹 서버를 띄울 때만 server.servlet.session.cookie.*를 세션 쿠키에 적용한다.
     * 외부 서블릿 컨테이너나 어댑터에서도 같은 속성을 쓰도록 직접 지정한다.
     */
    @Bean
    CookieSerializer cookieSerializer(@Value("${server.servlet.session.cookie.secure:true}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${desyp.frontend.origin}") String frontendOrigin) {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(frontendOrigin));
        config.setAllowedMethods(List.of("GET", "POST"));
        config.setAllowedHeaders(List.of("Content-Type"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/me", config);
        source.registerCorsConfiguration("/api/pre-registrations", config);
        source.registerCorsConfiguration("/api/referrals/me", config);
        source.registerCorsConfiguration("/api/conversions/kakao-channel-click", config);
        return source;
    }
}
