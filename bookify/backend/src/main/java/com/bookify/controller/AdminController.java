package com.bookify.controller;

import com.bookify.dto.Dtos.*;
import com.bookify.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AnalyticsService analytics;

    @GetMapping("/analytics/summary")
    public AnalyticsSummary summary(@RequestParam(defaultValue = "14") int days) {
        return analytics.summary(Math.min(Math.max(days, 1), 90));
    }

    @GetMapping("/users")
    public PageResponse<AdminUserDto> users(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "10") int size) {
        return PageResponse.from(analytics.users(PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50), Sort.by("id"))));
    }

    @PatchMapping("/users/{id}/enabled")
    public AdminUserDto setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        return analytics.setEnabled(id, value);
    }
}
