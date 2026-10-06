package com.bookify.service;

import com.bookify.dto.Dtos.*;
import com.bookify.entity.*;
import com.bookify.event.BookingEvent;
import com.bookify.exception.ApiException;
import com.bookify.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Double-booking protection, in three layers:
 *  1. Pessimistic lock on the provider row (SELECT ... FOR UPDATE), so bookings for one provider are checked one at a time.
 *  2. An overlap query inside that lock, which also catches partially overlapping times of different lengths.
 *  3. A partial unique index in the database (provider_id, start_time) as the last line of defence.
 * Status changes (confirm, cancel...) are protected by @Version on Booking.
 */
@Service
@RequiredArgsConstructor
public class BookingService {
    private static final String TOOK = "Someone just took that time. Please pick another slot.";

    private final BookingRepository bookings;
    private final ProviderProfileRepository providers;
    private final ServiceOfferingRepository services;
    private final UserRepository users;
    private final ReviewRepository reviews;
    private final SlotService slotService;
    private final ApplicationEventPublisher events;

    // ------------------------------------------------------------------ create

    @Transactional
    public BookingDto create(String email, BookingRequest r) {
        User customer = users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.unauthorized("Please log in again"));
        if (customer.getRole() != Role.CUSTOMER) throw ApiException.forbidden("Only customers can book appointments");

        ProviderProfile provider = providers.findByIdForUpdate(r.providerId())
                .orElseThrow(() -> ApiException.notFound("Provider not found"));
        ServiceOffering service = services.findByIdAndProviderId(r.serviceId(), provider.getId())
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> ApiException.notFound("That service isn't offered by this provider"));

        LocalDateTime start = r.startTime().withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(service.getDurationMinutes());
        checkBookable(provider.getId(), start, end, 0L);

        Booking b = new Booking();
        b.setProvider(provider);
        b.setService(service);
        b.setCustomer(customer);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setStatus(BookingStatus.PENDING);
        b.setNotes(r.notes());
        b = flush(b);

        events.publishEvent(BookingEvent.of(BookingEvent.Type.CREATED, b));
        return toDto(b);
    }

    // ------------------------------------------------------------------ read

    @Transactional(readOnly = true)
    public Page<BookingDto> mine(String email, BookingStatus status, int page, int size) {
        User u = users.findByEmailIgnoreCase(email).orElseThrow(() -> ApiException.unauthorized("Please log in again"));
        // upcoming work reads best soonest-first; history reads best newest-first
        boolean upcoming = status == BookingStatus.PENDING || status == BookingStatus.CONFIRMED;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(upcoming ? Sort.Direction.ASC : Sort.Direction.DESC, "startTime"));

        if (u.getRole() == Role.PROVIDER) {
            ProviderProfile p = providers.findByUserEmailIgnoreCase(email)
                    .orElseThrow(() -> ApiException.forbidden("Provider profile missing"));
            return (status == null ? bookings.findByProviderId(p.getId(), pageable)
                    : bookings.findByProviderIdAndStatus(p.getId(), status, pageable)).map(this::toDto);
        }
        if (u.getRole() == Role.CUSTOMER) {
            return (status == null ? bookings.findByCustomerId(u.getId(), pageable)
                    : bookings.findByCustomerIdAndStatus(u.getId(), status, pageable)).map(this::toDto);
        }
        return Page.empty(pageable);
    }

    // ------------------------------------------------------------------ status changes

    @Transactional
    public BookingDto cancel(String email, Long id) {
        Booking b = load(id);
        if (!isCustomer(b, email) && !isProvider(b, email)) throw ApiException.forbidden("This isn't your booking");
        if (!BookingStatus.ACTIVE.contains(b.getStatus()))
            throw ApiException.conflict("Only pending or confirmed bookings can be cancelled");
        b.setStatus(BookingStatus.CANCELLED);
        events.publishEvent(BookingEvent.of(BookingEvent.Type.CANCELLED, b));
        return toDto(b);
    }

    @Transactional
    public BookingDto confirm(String email, Long id) {
        Booking b = load(id);
        requireProvider(b, email);
        if (b.getStatus() != BookingStatus.PENDING) throw ApiException.conflict("Only pending bookings can be confirmed");
        b.setStatus(BookingStatus.CONFIRMED);
        events.publishEvent(BookingEvent.of(BookingEvent.Type.CONFIRMED, b));
        return toDto(b);
    }

    @Transactional
    public BookingDto reject(String email, Long id) {
        Booking b = load(id);
        requireProvider(b, email);
        if (b.getStatus() != BookingStatus.PENDING) throw ApiException.conflict("Only pending bookings can be rejected");
        b.setStatus(BookingStatus.REJECTED);
        events.publishEvent(BookingEvent.of(BookingEvent.Type.REJECTED, b));
        return toDto(b);
    }

    @Transactional
    public BookingDto complete(String email, Long id) {
        Booking b = load(id);
        requireProvider(b, email);
        if (b.getStatus() != BookingStatus.CONFIRMED) throw ApiException.conflict("Only confirmed bookings can be completed");
        b.setStatus(BookingStatus.COMPLETED);
        return toDto(b);
    }

    @Transactional
    public BookingDto reschedule(String email, Long id, RescheduleRequest r) {
        Booking b = load(id);
        boolean byCustomer = isCustomer(b, email);
        if (!byCustomer && !isProvider(b, email)) throw ApiException.forbidden("This isn't your booking");
        if (!BookingStatus.ACTIVE.contains(b.getStatus()))
            throw ApiException.conflict("Only pending or confirmed bookings can be rescheduled");

        providers.findByIdForUpdate(b.getProvider().getId()).orElseThrow(); // same lock as create()
        LocalDateTime start = r.startTime().withSecond(0).withNano(0);
        LocalDateTime end = start.plusMinutes(b.getService().getDurationMinutes());
        checkBookable(b.getProvider().getId(), start, end, b.getId());

        b.setStartTime(start);
        b.setEndTime(end);
        b.setReminderSent(false);
        // a customer moving a confirmed appointment needs the provider to agree to the new time
        if (byCustomer && b.getStatus() == BookingStatus.CONFIRMED) b.setStatus(BookingStatus.PENDING);
        b = flush(b);

        events.publishEvent(BookingEvent.of(BookingEvent.Type.RESCHEDULED, b));
        return toDto(b);
    }

    // ------------------------------------------------------------------ helpers

    private void checkBookable(Long providerId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        if (!start.isAfter(LocalDateTime.now())) throw ApiException.badRequest("Pick a time in the future");
        if (!slotService.fitsAvailability(providerId, start, end))
            throw ApiException.badRequest("That time is outside the provider's working hours");
        if (bookings.countOverlapping(providerId, start, end, BookingStatus.ACTIVE, excludeId) > 0)
            throw ApiException.conflict(TOOK);
    }

    /** Writes now (not at commit) so a database-level conflict surfaces here as a clean 409. */
    private Booking flush(Booking b) {
        try {
            return bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException e) {
            throw ApiException.conflict(TOOK);
        }
    }

    private Booking load(Long id) {
        return bookings.findById(id).orElseThrow(() -> ApiException.notFound("Booking not found"));
    }

    private boolean isCustomer(Booking b, String email) { return b.getCustomer().getEmail().equalsIgnoreCase(email); }
    private boolean isProvider(Booking b, String email) { return b.getProvider().getUser().getEmail().equalsIgnoreCase(email); }

    private void requireProvider(Booking b, String email) {
        if (!isProvider(b, email)) throw ApiException.forbidden("This booking belongs to another provider");
    }

    private BookingDto toDto(Booking b) {
        boolean reviewed = b.getStatus() == BookingStatus.COMPLETED && b.getId() != null && reviews.existsByBookingId(b.getId());
        return new BookingDto(b.getId(), b.getProvider().getId(), b.getProvider().getUser().getFullName(),
                b.getService().getId(), b.getService().getName(), b.getService().getPrice(),
                b.getCustomer().getFullName(), b.getStartTime(), b.getEndTime(), b.getStatus(), b.getNotes(), reviewed);
    }
}
