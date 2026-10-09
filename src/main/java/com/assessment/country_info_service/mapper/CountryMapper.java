package com.assessment.country_info_service.mapper;

import com.assessment.country_info_service.client.SoapCountryInfo;
import com.assessment.country_info_service.dto.CountryInfoResponse;
import com.assessment.country_info_service.dto.LanguageDto;
import com.assessment.country_info_service.dto.UpdateCountryRequest;
import com.assessment.country_info_service.model.CountryInfo;
import com.assessment.country_info_service.model.Language;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CountryMapper {

    public CountryInfoResponse toResponse(CountryInfo c) {
        List<LanguageDto> languages = c.getLanguages().stream()
                .map(l -> new LanguageDto(l.getIsoCode(), l.getName()))
                .toList();

        return new CountryInfoResponse(
                c.getId(), c.getIsoCode(), c.getName(),
                c.getCapitalCity(), c.getPhoneCode(),
                c.getContinentCode(), c.getCurrencyIsoCode(),
                c.getFlagUrl(), languages,
                c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    public CountryInfo toEntity(SoapCountryInfo s, String isoCode) {
        CountryInfo c = new CountryInfo();
        c.setIsoCode(isoCode);
        c.setName(s.name());
        c.setCapitalCity(s.capitalCity());
        c.setPhoneCode(s.phoneCode());
        c.setContinentCode(s.continentCode());
        c.setCurrencyIsoCode(s.currencyIsoCode());
        c.setFlagUrl(s.flagUrl());

        s.languages().forEach(l ->
                c.addLanguage(new Language(l.isoCode(), l.name())));

        return c;
    }

    public void applyUpdate(CountryInfo c, UpdateCountryRequest r) {
        if (r.name() != null) c.setName(r.name().trim());
        if (r.capitalCity() != null) c.setCapitalCity(r.capitalCity());
        if (r.phoneCode() != null) c.setPhoneCode(r.phoneCode());
        if (r.continentCode() != null) c.setContinentCode(r.continentCode());
        if (r.currencyIsoCode() != null) c.setCurrencyIsoCode(r.currencyIsoCode());
        if (r.flagUrl() != null) c.setFlagUrl(r.flagUrl());

        if (r.languages() != null) {
            c.getLanguages().clear();
            r.languages().forEach(l ->
                    c.addLanguage(new Language(l.isoCode(), l.name())));
        }
    }
}
