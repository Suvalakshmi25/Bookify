package com.bookify.entity;

import java.util.List;

public enum BookingStatus {
    PENDING, CONFIRMED, REJECTED, CANCELLED, COMPLETED;

    /** Statuses that occupy the provider's calendar. */
    public static final List<BookingStatus> ACTIVE = List.of(PENDING, CONFIRMED);
}
