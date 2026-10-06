package com.bookify;

import com.bookify.dto.Dtos.SlotDto;
import com.bookify.entity.*;
import com.bookify.repository.AvailabilityRuleRepository;
import com.bookify.repository.BookingRepository;
import com.bookify.repository.ServiceOfferingRepository;
import com.bookify.service.SlotService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SlotServiceTest {
    private final ServiceOfferingRepository services = mock(ServiceOfferingRepository.class);
    private final AvailabilityRuleRepository rules = mock(AvailabilityRuleRepository.class);
    private final BookingRepository bookings = mock(BookingRepository.class);
    private final SlotService slotService = new SlotService(services, rules, bookings);

    private final LocalDate day = LocalDate.now().plusDays(7);

    private void givenWindow(int fromHour, int toHour, int serviceMinutes) {
        ServiceOffering svc = new ServiceOffering();
        svc.setId(2L); svc.setDurationMinutes(serviceMinutes); svc.setPrice(BigDecimal.TEN);
        when(services.findByIdAndProviderId(2L, 1L)).thenReturn(Optional.of(svc));
        AvailabilityRule rule = new AvailabilityRule();
        rule.setDayOfWeek(day.getDayOfWeek());
        rule.setStartTime(LocalTime.of(fromHour, 0));
        rule.setEndTime(LocalTime.of(toHour, 0));
        when(rules.findByProviderIdAndDayOfWeek(1L, day.getDayOfWeek())).thenReturn(List.of(rule));
    }

    @Test
    void splitsWorkingHoursIntoServiceSizedSlots() {
        givenWindow(9, 11, 30);
        when(bookings.findActiveInRange(eq(1L), any(), any(), any())).thenReturn(List.of());

        List<SlotDto> slots = slotService.slots(1L, 2L, day);

        assertThat(slots).hasSize(4);
        assertThat(slots).allMatch(SlotDto::available);
        assertThat(slots.get(0).start()).isEqualTo(day.atTime(9, 0));
        assertThat(slots.get(3).end()).isEqualTo(day.atTime(11, 0));
    }

    @Test
    void marksSlotsThatOverlapAnExistingBookingAsUnavailable() {
        givenWindow(9, 11, 30);
        Booking busy = new Booking();
        busy.setStartTime(day.atTime(9, 30));
        busy.setEndTime(day.atTime(10, 0));
        when(bookings.findActiveInRange(eq(1L), any(), any(), any())).thenReturn(List.of(busy));

        List<SlotDto> slots = slotService.slots(1L, 2L, day);

        assertThat(slots.stream().map(SlotDto::available).toList()).containsExactly(true, false, true, true);
    }

    @Test
    void doesNotOfferSlotThatWouldRunPastClosingTime() {
        givenWindow(9, 10, 45); // only one 45-minute slot fits in an hour
        when(bookings.findActiveInRange(eq(1L), any(), any(), any())).thenReturn(List.of());

        assertThat(slotService.slots(1L, 2L, day)).hasSize(1);
    }
}
