package com.desyp.notification.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.desyp.common.response.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @GetMapping("/api/admin/stats")
    public ApiResponse<AdminStatsResponse> stats(@RequestParam(defaultValue = "14") @Min(1) @Max(90) int days) {
        return ApiResponse.success(adminStatsService.stats(days));
    }
}
