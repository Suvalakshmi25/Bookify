package com.bookify.repository;

import com.bookify.entity.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.DayOfWeek;
import java.util.List;

public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {
    List<AvailabilityRule> findByProviderIdOrderByDayOfWeekAscStartTimeAsc(Long providerId);
    List<AvailabilityRule> findByProviderIdAndDayOfWeek(Long providerId, DayOfWeek dayOfWeek);
    void deleteByProviderId(Long providerId);
}
