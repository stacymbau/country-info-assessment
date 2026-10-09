package com.assessment.country_info_service.repository;

import com.assessment.country_info_service.model.CountryInfo;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CountryInfoRepository extends JpaRepository<CountryInfo, Long> {

    @EntityGraph(attributePaths = "languages")
    Optional<CountryInfo> findByIsoCode(String isoCode);

    @EntityGraph(attributePaths = "languages")
    Optional<CountryInfo> findByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = "languages")
    Optional<CountryInfo> findWithLanguagesById(Long id);
}
