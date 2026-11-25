package com.desyp.notification.mail;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.desyp.common.response.ApiResponse;
import com.desyp.notification.mail.dto.MailSendResponse;

import lombok.RequiredArgsConstructor;

/** 관리자 대시보드(HTTP Basic)에서 호출한다. CSRF 대신 JSON 전용으로 교차 사이트 요청을 막는다(SecurityConfig). */
@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
public class MailController {

    private final RegistrationMailService registrationMailService;

    @PostMapping(path = "/event-start", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<MailSendResponse> sendEventStartNotifications() {
        return ApiResponse.success(new MailSendResponse(registrationMailService.sendEventStartNotifications()));
    }
}
