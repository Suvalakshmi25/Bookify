package com.bookify.config;

import com.bookify.entity.*;
import com.bookify.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Loads a small demo dataset on first start so the app is explorable immediately. Disable with SEED_DATA=false. */
@Component
@ConditionalOnProperty(name = "bookify.seed", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {
    public static final String DEMO_PASSWORD = "Password@123";

    private final UserRepository users;
    private final ProviderProfileRepository providers;
    private final ServiceOfferingRepository services;
    private final AvailabilityRuleRepository rules;
    private final BookingRepository bookings;
    private final ReviewRepository reviews;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;

        user("Admin", "admin@bookify.dev", Role.ADMIN);
        User customer = user("Demo Customer", "customer@bookify.dev", Role.CUSTOMER);

        List<ServiceOffering> allServices = new ArrayList<>();
        allServices.addAll(provider("Dr. Meera Iyer", "meera@bookify.dev", "Dentist",
                "General and cosmetic dentistry with 12 years of practice. Gentle with nervous patients.",
                new Object[][]{{"Check-up and cleaning", 30, "60.00"}, {"Teeth whitening", 60, "140.00"}}));
        allServices.addAll(provider("Arjun Nair", "arjun@bookify.dev", "Fitness trainer",
                "Strength and mobility coaching for desk workers. Sessions in person or on video.",
                new Object[][]{{"Intro session", 45, "25.00"}, {"Personal training", 60, "45.00"}}));
        allServices.addAll(provider("Sofia Alvarez", "sofia@bookify.dev", "Career coach",
                "Helps engineers and designers plan their next move: CVs, interviews, salary talks.",
                new Object[][]{{"CV review", 30, "35.00"}, {"Mock interview", 60, "70.00"}}));
        allServices.addAll(provider("Kabir Shah", "kabir@bookify.dev", "Legal consultant",
                "Contracts, tenancy and freelancer agreements explained in plain language.",
                new Object[][]{{"Contract review", 45, "90.00"}, {"Initial consultation", 30, "50.00"}}));

        // two weeks of completed history so the admin charts and ratings have something to show
        Random rnd = new Random(42);
        for (int i = 0; i < 28; i++) {
            ServiceOffering s = allServices.get(rnd.nextInt(allServices.size()));
            LocalDateTime start = LocalDateTime.now().minusDays(1 + rnd.nextInt(13))
                    .withHour(9 + rnd.nextInt(7)).withMinute(0).withSecond(0).withNano(0);
            Booking b = new Booking();
            b.setProvider(s.getProvider());
            b.setService(s);
            b.setCustomer(customer);
            b.setStartTime(start);
            b.setEndTime(start.plusMinutes(s.getDurationMinutes()));
            b.setStatus(BookingStatus.COMPLETED);
            bookings.save(b);
            if (rnd.nextInt(3) > 0) {
                Review r = new Review();
                r.setBooking(b);
                r.setProvider(s.getProvider());
                r.setCustomer(customer);
                r.setRating(3 + rnd.nextInt(3));
                r.setComment(r.getRating() >= 5 ? "Excellent, would book again." : "Good session, on time.");
                reviews.save(r);
                ProviderProfile p = s.getProvider();
                double total = p.getRatingAvg() * p.getRatingCount() + r.getRating();
                p.setRatingCount(p.getRatingCount() + 1);
                p.setRatingAvg(Math.round(total / p.getRatingCount() * 100.0) / 100.0);
                providers.save(p);
            }
        }
        log.info("Seeded demo data. Log in with admin@bookify.dev / customer@bookify.dev / meera@bookify.dev, password '{}'",
                DEMO_PASSWORD);
    }

    private User user(String name, String email, Role role) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        return users.save(u);
    }

    private List<ServiceOffering> provider(String name, String email, String category, String bio, Object[][] svcs) {
        User u = user(name, email, Role.PROVIDER);
        ProviderProfile p = new ProviderProfile();
        p.setUser(u);
        p.setCategory(category);
        p.setBio(bio);
        p = providers.save(p);

        for (DayOfWeek d : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            AvailabilityRule r = new AvailabilityRule();
            r.setProvider(p);
            r.setDayOfWeek(d);
            r.setStartTime(LocalTime.of(9, 0));
            r.setEndTime(LocalTime.of(17, 0));
            rules.save(r);
        }
        List<ServiceOffering> out = new ArrayList<>();
        for (Object[] s : svcs) {
            ServiceOffering o = new ServiceOffering();
            o.setProvider(p);
            o.setName((String) s[0]);
            o.setDurationMinutes((Integer) s[1]);
            o.setPrice(new BigDecimal((String) s[2]));
            out.add(services.save(o));
        }
        return out;
    }
}
