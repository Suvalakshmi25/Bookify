package com.bookify.repository;

import com.bookify.entity.ProviderProfile;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProviderProfileRepository
        extends JpaRepository<ProviderProfile, Long>, JpaSpecificationExecutor<ProviderProfile> {

    Optional<ProviderProfile> findByUserEmailIgnoreCase(String email);

    @Query("select distinct p.category from ProviderProfile p where p.user.enabled = true order by p.category")
    List<String> findCategories();

    /** SELECT ... FOR UPDATE: serialises writers (bookings, reviews) that touch the same provider. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ProviderProfile p where p.id = :id")
    Optional<ProviderProfile> findByIdForUpdate(@Param("id") Long id);
}
