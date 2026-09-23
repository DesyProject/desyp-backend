package com.desyp.notification.auth;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final LoginRedirect loginRedirect;

    @GetMapping("/auth/naver/login")
    public String naverLogin(@RequestParam(name = "return_to", required = false) String returnTo, HttpSession session) {
        loginRedirect.remember(session, returnTo);
        return "redirect:/oauth2/authorization/naver";
    }
}
