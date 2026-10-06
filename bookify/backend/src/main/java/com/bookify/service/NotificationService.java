package com.bookify.service;

import com.bookify.event.BookingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Emails are sent asynchronously and only AFTER the booking transaction commits,
 * so a slow SMTP server never delays the API and a rolled-back booking never emails anyone.
 */
@Service
@Slf4j
public class NotificationService {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String from;

    public NotificationService(ObjectProvider<JavaMailSender> mailSender,
                               @Value("${bookify.mail.enabled}") boolean enabled,
                               @Value("${bookify.mail.from}") String from) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent e) {
        String when = e.start().format(WHEN);
        String what = e.serviceName() + " on " + when;
        switch (e.type()) {
            case CREATED -> {
                send(e.providerEmail(), "New booking request", "Hi " + e.providerName() + ",\n\n" + e.customerName()
                        + " requested " + what + ". Open Bookify to confirm or reject it.");
                send(e.customerEmail(), "We've sent your request", "Hi " + e.customerName() + ",\n\nYour request for "
                        + what + " with " + e.providerName() + " is waiting for confirmation.");
            }
            case CONFIRMED -> send(e.customerEmail(), "Your booking is confirmed",
                    "Hi " + e.customerName() + ",\n\n" + e.providerName() + " confirmed " + what + ".");
            case REJECTED -> send(e.customerEmail(), "Your booking wasn't accepted",
                    "Hi " + e.customerName() + ",\n\n" + e.providerName() + " couldn't take " + what + ". Please pick another time.");
            case CANCELLED -> {
                send(e.customerEmail(), "Booking cancelled", "The booking for " + what + " with " + e.providerName() + " was cancelled.");
                send(e.providerEmail(), "Booking cancelled", "The booking for " + what + " with " + e.customerName() + " was cancelled.");
            }
            case RESCHEDULED -> {
                send(e.customerEmail(), "Booking time changed", "Your booking with " + e.providerName() + " is now " + what + ".");
                send(e.providerEmail(), "Booking time changed", e.customerName() + "'s booking is now " + what + ".");
            }
        }
    }

    @Async
    public void sendReminder(String to, String customerName, String providerName, String serviceName, LocalDateTime start) {
        send(to, "Reminder: appointment tomorrow", "Hi " + customerName + ",\n\nA reminder that you have "
                + serviceName + " with " + providerName + " on " + start.format(WHEN) + ".");
    }

    private void send(String to, String subject, String body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (!enabled || sender == null) {
            log.info("[mail disabled] to={} subject={}", to, subject);
            return;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject("Bookify: " + subject);
            m.setText(body);
            sender.send(m);
        } catch (Exception ex) {
            log.warn("Could not send mail to {}: {}", to, ex.getMessage());
        }
    }
}
