package com.bookify.service;

import com.bookify.dto.Dtos.ReviewDto;
import com.bookify.dto.Dtos.ReviewRequest;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.BookingRepository;
import com.bookify.repository.ProviderProfileRepository;
import com.bookify.repository.ReviewRepository;
import com.bookify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {
    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final UserRepository users;
    private final ProviderProfileRepository providers;

    @Transactional
    public ReviewDto create(String email, ReviewRequest r) {
        User customer = users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.unauthorized("Please log in again"));
        Booking b = bookings.findById(r.bookingId()).orElseThrow(() -> ApiException.notFound("Booking not found"));
        if (!b.getCustomer().getId().equals(customer.getId())) throw ApiException.forbidden("This isn't your booking");
        if (b.getStatus() != BookingStatus.COMPLETED) throw ApiException.badRequest("You can review an appointment after it's completed");
        if (reviews.existsByBookingId(b.getId())) throw ApiException.conflict("You already reviewed this appointment");

        Review review = new Review();
        review.setBooking(b);
        review.setProvider(b.getProvider());
        review.setCustomer(customer);
        review.setRating(r.rating());
        review.setComment(r.comment());
        reviews.save(review);

        // lock the provider row so two simultaneous reviews can't corrupt the running average
        ProviderProfile p = providers.findByIdForUpdate(b.getProvider().getId()).orElseThrow();
        double total = p.getRatingAvg() * p.getRatingCount() + r.rating();
        p.setRatingCount(p.getRatingCount() + 1);
        p.setRatingAvg(Math.round(total / p.getRatingCount() * 100.0) / 100.0);

        return toDto(review);
    }

    @Transactional(readOnly = true)
    public Page<ReviewDto> forProvider(Long providerId, Pageable pageable) {
        return reviews.findByProviderIdOrderByCreatedAtDesc(providerId, pageable).map(this::toDto);
    }

    private ReviewDto toDto(Review r) {
        return new ReviewDto(r.getId(), r.getCustomer().getFullName(), r.getRating(), r.getComment(), r.getCreatedAt());
    }
}
