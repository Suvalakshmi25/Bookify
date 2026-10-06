package com.bookify.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "provider_profiles")
@Getter @Setter @NoArgsConstructor
public class ProviderProfile {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 60)
    private String category;

    @Column(length = 1000)
    private String bio;

    @Column(nullable = false)
    private double ratingAvg;

    @Column(nullable = false)
    private int ratingCount;
}
