package com.bookify.controller;

import com.bookify.dto.Dtos.*;
import com.bookify.service.ProviderService;
import com.bookify.service.ReviewService;
import com.bookify.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Public discovery endpoints: search, profile, free slots, reviews. */
@RestController
@RequestMapping("/api/providers")
@RequiredArgsConstructor
public class ProviderController {
    private final ProviderService providers;
    private final SlotService slots;
    private final ReviewService reviews;

    @GetMapping
    public PageResponse<ProviderSummary> search(@RequestParam(required = false) String q,
                                                @RequestParam(required = false) String category,
                                                @RequestParam(required = false) Double minRating,
                                                @RequestParam(defaultValue = "rating") String sort,
                                                @RequestParam(defaultValue = "desc") String dir,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "9") int size) {
        // whitelist sort keys: never pass raw user input into a query
        String property = switch (sort) {
            case "name" -> "user.fullName";
            case "reviews" -> "ratingCount";
            default -> "ratingAvg";
        };
        Sort.Direction direction = "asc".equalsIgnoreCase(dir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(direction, property).and(Sort.by(Sort.Direction.ASC, "id")));
        return PageResponse.from(providers.search(category, q, minRating, pageable));
    }

    @GetMapping("/categories")
    public List<String> categories() { return providers.categories(); }

    @GetMapping("/{id}")
    public ProviderDetail detail(@PathVariable Long id) { return providers.detail(id); }

    @GetMapping("/{id}/slots")
    public List<SlotDto> slots(@PathVariable Long id, @RequestParam Long serviceId,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return slots.slots(id, serviceId, date);
    }

    @GetMapping("/{id}/reviews")
    public PageResponse<ReviewDto> reviews(@PathVariable Long id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size) {
        return PageResponse.from(reviews.forProvider(id, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50))));
    }
}
