package com.desyp.notification.mail;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.desyp.common.response.ApiResponse;
import com.desyp.notification.mail.dto.MailSendResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/mail")
@RequiredArgsConstructor
public class MailController {

    private final RegistrationMailService registrationMailService;

    @PostMapping("/event-start")
    public ApiResponse<MailSendResponse> sendEventStartNotifications() {
        return ApiResponse.success(new MailSendResponse(registrationMailService.sendEventStartNotifications()));
    }
}
