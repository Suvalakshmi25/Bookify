package com.bookify.service;

import com.bookify.dto.Dtos.*;
import com.bookify.entity.*;
import com.bookify.exception.ApiException;
import com.bookify.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProviderService {
    private final ProviderProfileRepository providers;
    private final ServiceOfferingRepository services;
    private final AvailabilityRuleRepository rules;

    // ---------- public discovery ----------

    @Transactional(readOnly = true)
    public Page<ProviderSummary> search(String category, String q, Double minRating, Pageable pageable) {
        Specification<ProviderProfile> spec = (root, query, cb) -> {
            var user = root.join("user");
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isTrue(user.<Boolean>get("enabled")));
            if (category != null && !category.isBlank())
                ps.add(cb.equal(cb.lower(root.<String>get("category")), category.trim().toLowerCase()));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(user.<String>get("fullName")), like),
                        cb.like(cb.lower(root.<String>get("category")), like),
                        cb.like(cb.lower(root.<String>get("bio")), like)));
            }
            if (minRating != null && minRating > 0)
                ps.add(cb.greaterThanOrEqualTo(root.<Double>get("ratingAvg"), minRating));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return providers.findAll(spec, pageable).map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public List<String> categories() { return providers.findCategories(); }

    @Transactional(readOnly = true)
    public ProviderDetail detail(Long id) {
        ProviderProfile p = providers.findById(id).orElseThrow(() -> ApiException.notFound("Provider not found"));
        if (!p.getUser().isEnabled()) throw ApiException.notFound("Provider not found");
        return new ProviderDetail(p.getId(), p.getUser().getFullName(), p.getCategory(), p.getBio(),
                p.getRatingAvg(), p.getRatingCount(),
                services.findByProviderIdAndActiveTrueOrderByName(id).stream().map(this::toDto).toList(),
                rules.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(id).stream().map(this::toDto).toList());
    }

    // ---------- provider self-management ----------

    @Transactional(readOnly = true)
    public List<ServiceDto> myServices(String email) {
        return services.findByProviderIdAndActiveTrueOrderByName(me(email).getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public ServiceDto createService(String email, ServiceRequest r) {
        ServiceOffering s = new ServiceOffering();
        s.setProvider(me(email));
        apply(s, r);
        return toDto(services.save(s));
    }

    @Transactional
    public ServiceDto updateService(String email, Long id, ServiceRequest r) {
        ServiceOffering s = owned(email, id);
        apply(s, r);
        return toDto(s);
    }

    /** Soft delete: old bookings keep pointing at the service they were made for. */
    @Transactional
    public void deleteService(String email, Long id) {
        owned(email, id).setActive(false);
    }

    @Transactional(readOnly = true)
    public List<AvailabilityDto> myAvailability(String email) {
        return rules.findByProviderIdOrderByDayOfWeekAscStartTimeAsc(me(email).getId()).stream().map(this::toDto).toList();
    }

    @Transactional
    public List<AvailabilityDto> replaceAvailability(String email, AvailabilityUpdateRequest req) {
        ProviderProfile p = me(email);
        for (AvailabilityDto d : req.rules()) {
            if (!d.endTime().isAfter(d.startTime()))
                throw ApiException.badRequest("Closing time must be after opening time");
        }
        // overlapping windows on the same day would create duplicate slots
        for (int i = 0; i < req.rules().size(); i++) {
            for (int j = i + 1; j < req.rules().size(); j++) {
                AvailabilityDto a = req.rules().get(i), b = req.rules().get(j);
                if (a.dayOfWeek() == b.dayOfWeek() && a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime()))
                    throw ApiException.badRequest("Two time windows on " + a.dayOfWeek() + " overlap");
            }
        }
        rules.deleteByProviderId(p.getId());
        rules.flush();
        for (AvailabilityDto d : req.rules()) {
            AvailabilityRule r = new AvailabilityRule();
            r.setProvider(p);
            r.setDayOfWeek(d.dayOfWeek());
            r.setStartTime(d.startTime());
            r.setEndTime(d.endTime());
            rules.save(r);
        }
        return myAvailability(email);
    }

    // ---------- helpers ----------

    private ProviderProfile me(String email) {
        return providers.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> ApiException.forbidden("Only providers can do this"));
    }

    private ServiceOffering owned(String email, Long serviceId) {
        ProviderProfile p = me(email);
        return services.findByIdAndProviderId(serviceId, p.getId())
                .orElseThrow(() -> ApiException.notFound("Service not found"));
    }

    private void apply(ServiceOffering s, ServiceRequest r) {
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setDurationMinutes(r.durationMinutes());
        s.setPrice(r.price());
    }

    private ProviderSummary toSummary(ProviderProfile p) {
        return new ProviderSummary(p.getId(), p.getUser().getFullName(), p.getCategory(), p.getBio(),
                p.getRatingAvg(), p.getRatingCount());
    }

    private ServiceDto toDto(ServiceOffering s) {
        return new ServiceDto(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(), s.getPrice(), s.isActive());
    }

    private AvailabilityDto toDto(AvailabilityRule r) {
        return new AvailabilityDto(r.getDayOfWeek(), r.getStartTime(), r.getEndTime());
    }
}
