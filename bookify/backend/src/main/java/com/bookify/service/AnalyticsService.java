package com.bookify.service;

import com.bookify.dto.Dtos.*;
import com.bookify.entity.Booking;
import com.bookify.entity.BookingStatus;
import com.bookify.entity.Role;
import com.bookify.entity.User;
import com.bookify.exception.ApiException;
import com.bookify.repository.BookingRepository;
import com.bookify.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {
    private static final List<BookingStatus> EARNING = List.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

    private final BookingRepository bookings;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public AnalyticsSummary summary(int days) {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(days - 1L);

        Map<LocalDate, Long> perDay = bookings
                .findByStartTimeBetweenAndStatusIn(from.atStartOfDay(), today.plusDays(1).atStartOfDay(), EARNING)
                .stream().collect(Collectors.groupingBy(b -> b.getStartTime().toLocalDate(), Collectors.counting()));

        List<DayCount> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1))
            series.add(new DayCount(d, perDay.getOrDefault(d, 0L)));

        List<TopProvider> top = bookings.topProviders(EARNING, PageRequest.of(0, 5)).stream()
                .map(row -> new TopProvider((Long) row[0], (String) row[1], (Long) row[2])).toList();

        BigDecimal revenue = bookings.revenue(EARNING);
        return new AnalyticsSummary(users.count(), users.countByRole(Role.PROVIDER), users.countByRole(Role.CUSTOMER),
                bookings.count(), revenue == null ? BigDecimal.ZERO : revenue, series, top);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserDto> users(Pageable pageable) {
        return users.findAll(pageable).map(u ->
                new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt()));
    }

    @Transactional
    public AdminUserDto setEnabled(Long id, boolean enabled) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == Role.ADMIN) throw ApiException.badRequest("Admin accounts can't be disabled here");
        u.setEnabled(enabled);
        return new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
