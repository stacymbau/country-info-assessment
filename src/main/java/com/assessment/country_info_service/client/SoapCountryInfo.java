package com.assessment.country_info_service.client;

import java.util.List;

/** Parsed FullCountryInfo SOAP response. */
public record SoapCountryInfo(
        String isoCode,
        String name,
        String capitalCity,
        String phoneCode,
        String continentCode,
        String currencyIsoCode,
        String flagUrl,
        List<SoapLanguage> languages) {

    public record SoapLanguage(String isoCode, String name) {
    }
}