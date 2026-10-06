package com.bookify.controller;

import com.bookify.dto.Dtos.*;
import com.bookify.entity.BookingStatus;
import com.bookify.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** Customers create/cancel/reschedule; providers confirm/reject/complete. The service enforces who may do what. */
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService bookings;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingDto create(Authentication a, @Valid @RequestBody BookingRequest r) { return bookings.create(a.getName(), r); }

    /** Customers see their own bookings; providers see bookings made with them. */
    @GetMapping("/me")
    public PageResponse<BookingDto> mine(Authentication a,
                                         @RequestParam(required = false) BookingStatus status,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "8") int size) {
        return PageResponse.from(bookings.mine(a.getName(), status, page, size));
    }

    @PatchMapping("/{id}/cancel")
    public BookingDto cancel(Authentication a, @PathVariable Long id) { return bookings.cancel(a.getName(), id); }

    @PatchMapping("/{id}/confirm")
    public BookingDto confirm(Authentication a, @PathVariable Long id) { return bookings.confirm(a.getName(), id); }

    @PatchMapping("/{id}/reject")
    public BookingDto reject(Authentication a, @PathVariable Long id) { return bookings.reject(a.getName(), id); }

    @PatchMapping("/{id}/complete")
    public BookingDto complete(Authentication a, @PathVariable Long id) { return bookings.complete(a.getName(), id); }

    @PatchMapping("/{id}/reschedule")
    public BookingDto reschedule(Authentication a, @PathVariable Long id, @Valid @RequestBody RescheduleRequest r) {
        return bookings.reschedule(a.getName(), id, r);
    }
}
