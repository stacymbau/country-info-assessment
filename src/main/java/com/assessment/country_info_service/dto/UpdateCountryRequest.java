package com.assessment.country_info_service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Fields left null are unchanged.
 */
public record UpdateCountryRequest(
        @Size(max = 150)
        @Pattern(regexp = ".*\\S.*", message = "name must not be blank")
        String name,

        @Size(max = 150)
        String capitalCity,

        @Size(max = 20)
        String phoneCode,

        @Size(max = 5)
        String continentCode,

        @Size(max = 5)
        String currencyIsoCode,

        @Size(max = 500)
        String flagUrl,

        @Valid
        List<LanguageDto> languages
) {
}
