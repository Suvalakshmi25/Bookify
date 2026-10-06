package com.bookify.controller;

import com.bookify.dto.Dtos.*;
import com.bookify.service.ProviderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Everything a provider does to their own profile. Secured by role in SecurityConfig (/api/provider/**). */
@RestController
@RequestMapping("/api/provider")
@RequiredArgsConstructor
public class ProviderManagementController {
    private final ProviderService providers;

    @GetMapping("/services")
    public List<ServiceDto> services(Authentication a) { return providers.myServices(a.getName()); }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto create(Authentication a, @Valid @RequestBody ServiceRequest r) {
        return providers.createService(a.getName(), r);
    }

    @PutMapping("/services/{id}")
    public ServiceDto update(Authentication a, @PathVariable Long id, @Valid @RequestBody ServiceRequest r) {
        return providers.updateService(a.getName(), id, r);
    }

    @DeleteMapping("/services/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(Authentication a, @PathVariable Long id) { providers.deleteService(a.getName(), id); }

    @GetMapping("/availability")
    public List<AvailabilityDto> availability(Authentication a) { return providers.myAvailability(a.getName()); }

    @PutMapping("/availability")
    public List<AvailabilityDto> setAvailability(Authentication a, @Valid @RequestBody AvailabilityUpdateRequest r) {
        return providers.replaceAvailability(a.getName(), r);
    }
}
