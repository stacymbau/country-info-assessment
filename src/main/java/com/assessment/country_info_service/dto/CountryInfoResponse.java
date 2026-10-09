package com.assessment.country_info_service.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CountryInfoResponse(
        Long id,
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String flagUrl,
        List<LanguageDto> languages,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
