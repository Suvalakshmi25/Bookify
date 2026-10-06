package com.bookify.service;

import com.bookify.dto.Dtos.SlotDto;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.AvailabilityRuleRepository;
import com.bookify.repository.BookingRepository;
import com.bookify.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Turns a provider's weekly working hours into concrete bookable slots for a given day and service. */
@Service
@RequiredArgsConstructor
public class SlotService {
    private final ServiceOfferingRepository services;
    private final AvailabilityRuleRepository rules;
    private final BookingRepository bookings;

    @Transactional(readOnly = true)
    public List<SlotDto> slots(Long providerId, Long serviceId, LocalDate date) {
        ServiceOffering svc = services.findByIdAndProviderId(serviceId, providerId)
                .filter(ServiceOffering::isActive)
                .orElseThrow(() -> ApiException.notFound("Service not found"));

        List<AvailabilityRule> windows = new ArrayList<>(rules.findByProviderIdAndDayOfWeek(providerId, date.getDayOfWeek()));
        windows.sort(Comparator.comparing(AvailabilityRule::getStartTime));
        if (windows.isEmpty()) return List.of();

        List<Booking> busy = bookings.findActiveInRange(providerId, date.atStartOfDay(),
                date.plusDays(1).atStartOfDay(), BookingStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();
        int minutes = svc.getDurationMinutes();

        List<SlotDto> out = new ArrayList<>();
        for (AvailabilityRule w : windows) {
            LocalDateTime cursor = date.atTime(w.getStartTime());
            LocalDateTime limit = date.atTime(w.getEndTime());
            while (!cursor.plusMinutes(minutes).isAfter(limit)) {
                final LocalDateTime from = cursor;
                final LocalDateTime to = cursor.plusMinutes(minutes);
                boolean taken = busy.stream().anyMatch(b -> b.getStartTime().isBefore(to) && b.getEndTime().isAfter(from));
                out.add(new SlotDto(from, to, from.isAfter(now) && !taken));
                cursor = to;
            }
        }
        return out;
    }

    /** True if [start, end) lies completely inside one of the provider's working windows on that weekday. */
    @Transactional(readOnly = true)
    public boolean fitsAvailability(Long providerId, LocalDateTime start, LocalDateTime end) {
        if (!start.toLocalDate().equals(end.toLocalDate())) return false;
        return rules.findByProviderIdAndDayOfWeek(providerId, start.getDayOfWeek()).stream()
                .anyMatch(r -> !start.toLocalTime().isBefore(r.getStartTime()) && !end.toLocalTime().isAfter(r.getEndTime()));
    }
}
