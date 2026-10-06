package com.bookify.event;

import com.bookify.entity.Booking;

import java.time.LocalDateTime;

/** Plain-data event (no lazy entities) so listeners can safely run on another thread. */
public record BookingEvent(Type type, String customerEmail, String customerName, String providerEmail,
                           String providerName, String serviceName, LocalDateTime start) {

    public enum Type { CREATED, CONFIRMED, REJECTED, CANCELLED, RESCHEDULED }

    public static BookingEvent of(Type type, Booking b) {
        return new BookingEvent(type,
                b.getCustomer().getEmail(), b.getCustomer().getFullName(),
                b.getProvider().getUser().getEmail(), b.getProvider().getUser().getFullName(),
                b.getService().getName(), b.getStartTime());
    }
}
