package com.bookify.dto;

import com.bookify.entity.BookingStatus;
import com.bookify.entity.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** All request/response shapes live here as records to keep the API surface easy to scan. */
public final class Dtos {
    private Dtos() {}

    // ---------- auth ----------
    public record RegisterRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 72, message = "Use at least 8 characters") String password,
            @NotNull Role role,
            @Size(max = 60) String category,
            @Size(max = 1000) String bio) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record UserInfo(Long id, String fullName, String email, Role role) {}
    public record AuthResponse(String accessToken, String refreshToken, UserInfo user) {}

    // ---------- providers ----------
    public record ProviderSummary(Long id, String name, String category, String bio,
                                  double ratingAvg, int ratingCount) {}

    public record ServiceDto(Long id, String name, String description, int durationMinutes,
                             BigDecimal price, boolean active) {}

    public record ServiceRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            @Min(5) @Max(480) int durationMinutes,
            @NotNull @DecimalMin("0.00") BigDecimal price) {}

    public record AvailabilityDto(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startTime, @NotNull LocalTime endTime) {}
    public record AvailabilityUpdateRequest(@NotNull @Valid List<AvailabilityDto> rules) {}

    public record ProviderDetail(Long id, String name, String category, String bio,
                                 double ratingAvg, int ratingCount,
                                 List<ServiceDto> services, List<AvailabilityDto> availability) {}

    public record SlotDto(LocalDateTime start, LocalDateTime end, boolean available) {}

    // ---------- bookings ----------
    public record BookingRequest(@NotNull Long providerId, @NotNull Long serviceId,
                                 @NotNull LocalDateTime startTime, @Size(max = 500) String notes) {}

    public record RescheduleRequest(@NotNull LocalDateTime startTime) {}

    public record BookingDto(Long id, Long providerId, String providerName, Long serviceId, String serviceName,
                             BigDecimal price, String customerName, LocalDateTime startTime, LocalDateTime endTime,
                             BookingStatus status, String notes, boolean reviewed) {}

    // ---------- reviews ----------
    public record ReviewRequest(@NotNull Long bookingId, @Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}
    public record ReviewDto(Long id, String customerName, int rating, String comment, LocalDateTime createdAt) {}

    // ---------- admin ----------
    public record DayCount(LocalDate date, long count) {}
    public record TopProvider(Long providerId, String name, long bookings) {}
    public record AnalyticsSummary(long totalUsers, long totalProviders, long totalCustomers, long totalBookings,
                                   BigDecimal revenue, List<DayCount> bookingsPerDay, List<TopProvider> topProviders) {}
    public record AdminUserDto(Long id, String fullName, String email, Role role, boolean enabled, LocalDateTime createdAt) {}

    // ---------- paging ----------
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(Page<T> p) {
            return new PageResponse<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
        }
    }
}
