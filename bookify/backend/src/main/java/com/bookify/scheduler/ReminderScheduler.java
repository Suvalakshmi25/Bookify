package com.bookify.scheduler;

import com.bookify.entity.Booking;
import com.bookify.entity.BookingStatus;
import com.bookify.repository.BookingRepository;
import com.bookify.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Every 5 minutes: email customers whose confirmed appointment starts within the next 24 hours. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {
    private final BookingRepository bookings;
    private final NotificationService notifications;

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 30 * 1000)
    @Transactional
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Booking> due = bookings.findDueForReminder(BookingStatus.CONFIRMED, now, now.plusHours(24));
        for (Booking b : due) {
            notifications.sendReminder(b.getCustomer().getEmail(), b.getCustomer().getFullName(),
                    b.getProvider().getUser().getFullName(), b.getService().getName(), b.getStartTime());
            b.setReminderSent(true);
        }
        if (!due.isEmpty()) log.info("Queued {} reminder email(s)", due.size());
    }
}
