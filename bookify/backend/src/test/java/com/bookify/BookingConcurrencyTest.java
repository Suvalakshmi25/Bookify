package com.bookify;

import com.bookify.dto.Dtos.BookingRequest;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.*;
import com.bookify.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The headline test: many customers hit "book" for the same slot at the same moment.
 * Exactly one must win; everyone else must get a clean ApiException (HTTP 409), not a 500 or a duplicate row.
 */
@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencyTest {
    @Autowired BookingService bookingService;
    @Autowired UserRepository users;
    @Autowired ProviderProfileRepository providers;
    @Autowired ServiceOfferingRepository services;
    @Autowired AvailabilityRuleRepository rules;
    @Autowired BookingRepository bookings;

    @Test
    void onlyOneOfManyConcurrentRequestsGetsTheSlot() throws Exception {
        User pu = saveUser("provider@race.dev", Role.PROVIDER);
        ProviderProfile provider = new ProviderProfile();
        provider.setUser(pu); provider.setCategory("Test");
        provider = providers.save(provider);

        ServiceOffering svc = new ServiceOffering();
        svc.setProvider(provider); svc.setName("Slot"); svc.setDurationMinutes(30); svc.setPrice(BigDecimal.TEN);
        svc = services.save(svc);

        LocalDate day = LocalDate.now().plusDays(3);
        AvailabilityRule rule = new AvailabilityRule();
        rule.setProvider(provider); rule.setDayOfWeek(day.getDayOfWeek());
        rule.setStartTime(LocalTime.of(9, 0)); rule.setEndTime(LocalTime.of(17, 0));
        rules.save(rule);

        int contenders = 8;
        for (int i = 0; i < contenders; i++) saveUser("c" + i + "@race.dev", Role.CUSTOMER);

        LocalDateTime start = day.atTime(10, 0);
        BookingRequest req = new BookingRequest(provider.getId(), svc.getId(), start, null);

        ExecutorService pool = Executors.newFixedThreadPool(contenders);
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < contenders; i++) {
            String email = "c" + i + "@race.dev";
            results.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    bookingService.create(email, req);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            }));
        }
        ready.await();
        go.countDown();

        int winners = 0;
        for (Future<Boolean> f : results) if (f.get(30, TimeUnit.SECONDS)) winners++;
        pool.shutdown();

        assertThat(winners).isEqualTo(1);
        assertThat(bookings.findActiveInRange(provider.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay(),
                BookingStatus.ACTIVE)).hasSize(1);
    }

    private User saveUser(String email, Role role) {
        User u = new User();
        u.setFullName(email); u.setEmail(email); u.setPasswordHash("x"); u.setRole(role);
        return users.save(u);
    }
}
