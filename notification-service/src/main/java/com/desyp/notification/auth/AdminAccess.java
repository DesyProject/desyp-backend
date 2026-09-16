package com.desyp.notification.auth;

import java.util.List;
import com.desyp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AdminAccess {
    private final List<String> googleSubjects;

    public AdminAccess(@Value("${desyp.admin.google-subs:}") List<String> googleSubjects) {
        this.googleSubjects = List.copyOf(googleSubjects);
    }

    public boolean isAllowed(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) return false;
        try {
            return googleSubjects.contains(GoogleAccount.require(token).getSubject());
        } catch (BusinessException exception) {
            return false;
        }
    }
}
