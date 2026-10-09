package com.assessment.country_info_service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LanguageDto(
        @Size(max = 10, message = "language isoCode must be at most 10 characters")
        String isoCode,

        @NotBlank(message = "language name is required")
        @Size(max = 100, message = "language name must be at most 100 characters")
        String name
) {
}
