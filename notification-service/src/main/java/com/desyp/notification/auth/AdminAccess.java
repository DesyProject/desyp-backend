package com.desyp.notification.auth;

import java.util.List;
import com.desyp.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class AdminAccess {
    private final List<String> naverAccountIds;

    public AdminAccess(@Value("${desyp.admin.naver-account-ids:}") List<String> naverAccountIds) {
        this.naverAccountIds = List.copyOf(naverAccountIds);
    }

    public boolean isAllowed(Authentication authentication) {
        if (!(authentication instanceof OAuth2AuthenticationToken token)) return false;
        try {
            return naverAccountIds.contains(SocialAccount.require(token).accountId());
        } catch (BusinessException exception) {
            return false;
        }
    }
}
