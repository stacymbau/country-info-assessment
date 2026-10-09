package com.assessment.country_info_service.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@Setter
@Entity
@Table(name = "country_info")
public class CountryInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "iso_code", nullable = false, unique = true, length = 2)
    private String isoCode;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "capital_city", length = 150)
    private String capitalCity;

    @Column(name = "phone_code", length = 20)
    private String phoneCode;

    @Column(name = "continent_code", length = 5)
    private String continentCode;

    @Column(name = "currency_iso_code", length = 5)
    private String currencyIsoCode;

    @Column(name = "flag_url", length = 500)
    private String flagUrl;

    @OneToMany(mappedBy = "countryInfo", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 50)
    private List<Language> languages = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public void addLanguage(Language language) {
        languages.add(language);
        language.setCountryInfo(this);
    }

}
