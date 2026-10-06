package com.bookify;

import com.bookify.dto.Dtos.BookingRequest;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.*;
import com.bookify.service.BookingService;
import com.bookify.service.SlotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {
    @Mock BookingRepository bookings;
    @Mock ProviderProfileRepository providers;
    @Mock ServiceOfferingRepository services;
    @Mock UserRepository users;
    @Mock ReviewRepository reviews;
    @Mock SlotService slotService;
    @Mock ApplicationEventPublisher events;
    @InjectMocks BookingService bookingService;

    User customer, providerUser;
    ProviderProfile provider;
    ServiceOffering service;
    LocalDateTime start;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L); customer.setEmail("c@test.dev"); customer.setFullName("Cara"); customer.setRole(Role.CUSTOMER);
        providerUser = new User();
        providerUser.setId(2L); providerUser.setEmail("p@test.dev"); providerUser.setFullName("Pat"); providerUser.setRole(Role.PROVIDER);
        provider = new ProviderProfile();
        provider.setId(10L); provider.setUser(providerUser);
        service = new ServiceOffering();
        service.setId(20L); service.setProvider(provider); service.setName("Consult");
        service.setDurationMinutes(30); service.setPrice(BigDecimal.TEN);
        start = LocalDateTime.now().plusDays(2).withHour(10).withMinute(0).withSecond(0).withNano(0);
    }

    private BookingRequest request() { return new BookingRequest(10L, 20L, start, null); }

    private void stubHappyPath() {
        when(users.findByEmailIgnoreCase("c@test.dev")).thenReturn(Optional.of(customer));
        when(providers.findByIdForUpdate(10L)).thenReturn(Optional.of(provider));
        when(services.findByIdAndProviderId(20L, 10L)).thenReturn(Optional.of(service));
    }

    @Test
    void createsPendingBookingWhenSlotIsFree() {
        stubHappyPath();
        when(slotService.fitsAvailability(eq(10L), any(), any())).thenReturn(true);
        when(bookings.countOverlapping(eq(10L), any(), any(), any(), anyLong())).thenReturn(0L);
        when(bookings.saveAndFlush(any(Booking.class))).thenAnswer(i -> i.getArgument(0));

        var dto = bookingService.create("c@test.dev", request());

        assertThat(dto.status()).isEqualTo(BookingStatus.PENDING);
        assertThat(dto.endTime()).isEqualTo(start.plusMinutes(30));
        verify(events).publishEvent(any(Object.class));
    }

    @Test
    void rejectsOverlappingBookingWith409() {
        stubHappyPath();
        when(slotService.fitsAvailability(eq(10L), any(), any())).thenReturn(true);
        when(bookings.countOverlapping(eq(10L), any(), any(), any(), anyLong())).thenReturn(1L);

        assertThatThrownBy(() -> bookingService.create("c@test.dev", request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("took that time");
        verify(bookings, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSlotOutsideWorkingHours() {
        stubHappyPath();
        when(slotService.fitsAvailability(eq(10L), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.create("c@test.dev", request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("working hours");
    }

    @Test
    void rejectsPastTimes() {
        stubHappyPath();
        var past = new BookingRequest(10L, 20L, LocalDateTime.now().minusHours(1), null);
        assertThatThrownBy(() -> bookingService.create("c@test.dev", past))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("future");
    }

    @Test
    void providersCannotBook() {
        when(users.findByEmailIgnoreCase("p@test.dev")).thenReturn(Optional.of(providerUser));
        assertThatThrownBy(() -> bookingService.create("p@test.dev", request()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Only customers");
    }

    @Test
    void confirmFailsForAnotherProvidersBooking() {
        Booking b = new Booking();
        b.setId(5L); b.setProvider(provider); b.setCustomer(customer); b.setService(service);
        b.setStatus(BookingStatus.PENDING);
        when(bookings.findById(5L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> bookingService.confirm("someone-else@test.dev", 5L))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("another provider");
    }
}
