package com.assessment.country_info_service.dto;

/**
 * Outcome of an onboarding call: the stored country
 * and whether this call created it.
 */
public record CountryResult(
        CountryInfoResponse body,
        boolean created
) {
}