package com.bookify.controller;

import com.bookify.dto.Dtos.ReviewDto;
import com.bookify.dto.Dtos.ReviewRequest;
import com.bookify.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController {
    private final ReviewService reviews;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto create(Authentication a, @Valid @RequestBody ReviewRequest r) {
        return reviews.create(a.getName(), r);
    }
}
