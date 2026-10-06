package com.bookify.repository;

import com.bookify.entity.Booking;
import com.bookify.entity.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // ---- listing (graph avoids N+1 when mapping to DTOs) ----
    @EntityGraph(attributePaths = {"provider.user", "service", "customer"})
    Page<Booking> findByCustomerId(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"provider.user", "service", "customer"})
    Page<Booking> findByCustomerIdAndStatus(Long customerId, BookingStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"provider.user", "service", "customer"})
    Page<Booking> findByProviderId(Long providerId, Pageable pageable);

    @EntityGraph(attributePaths = {"provider.user", "service", "customer"})
    Page<Booking> findByProviderIdAndStatus(Long providerId, BookingStatus status, Pageable pageable);

    // ---- availability / double-booking checks ----
    @Query("""
           select count(b) from Booking b
           where b.provider.id = :providerId and b.status in :statuses
             and b.startTime < :slotEnd and b.endTime > :slotStart and b.id <> :excludeId
           """)
    long countOverlapping(@Param("providerId") Long providerId,
                          @Param("slotStart") LocalDateTime slotStart,
                          @Param("slotEnd") LocalDateTime slotEnd,
                          @Param("statuses") Collection<BookingStatus> statuses,
                          @Param("excludeId") Long excludeId);

    @Query("""
           select b from Booking b
           where b.provider.id = :providerId and b.status in :statuses
             and b.startTime < :rangeEnd and b.endTime > :rangeStart
           """)
    List<Booking> findActiveInRange(@Param("providerId") Long providerId,
                                    @Param("rangeStart") LocalDateTime rangeStart,
                                    @Param("rangeEnd") LocalDateTime rangeEnd,
                                    @Param("statuses") Collection<BookingStatus> statuses);

    // ---- reminders ----
    @Query("""
           select b from Booking b
           where b.status = :status and b.reminderSent = false
             and b.startTime between :now and :until
           """)
    List<Booking> findDueForReminder(@Param("status") BookingStatus status,
                                     @Param("now") LocalDateTime now,
                                     @Param("until") LocalDateTime until);

    // ---- analytics ----
    List<Booking> findByStartTimeBetweenAndStatusIn(LocalDateTime from, LocalDateTime to,
                                                    Collection<BookingStatus> statuses);

    @Query("""
           select p.id, u.fullName, count(b) from Booking b
           join b.provider p join p.user u
           where b.status in :statuses
           group by p.id, u.fullName
           order by count(b) desc
           """)
    List<Object[]> topProviders(@Param("statuses") Collection<BookingStatus> statuses, Pageable pageable);

    @Query("select sum(s.price) from Booking b join b.service s where b.status in :statuses")
    BigDecimal revenue(@Param("statuses") Collection<BookingStatus> statuses);
}
