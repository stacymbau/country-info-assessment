package com.assessment.country_info_service.service;

import com.assessment.country_info_service.client.CountryInfoSoapClient;
import com.assessment.country_info_service.client.SoapCountryInfo;
import com.assessment.country_info_service.dto.CountryInfoResponse;
import com.assessment.country_info_service.dto.CountryResult;
import com.assessment.country_info_service.dto.PageResponse;
import com.assessment.country_info_service.dto.UpdateCountryRequest;
import com.assessment.country_info_service.exception.ExternalServiceException;
import com.assessment.country_info_service.exception.ResourceNotFoundException;
import com.assessment.country_info_service.mapper.CountryMapper;
import com.assessment.country_info_service.model.CountryInfo;
import com.assessment.country_info_service.repository.CountryInfoRepository;
import com.assessment.country_info_service.util.CountryNameNormalizer;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CountryService {

    private static final Logger log =
            LoggerFactory.getLogger(CountryService.class);

    private final CountryInfoRepository repository;
    private final CountryInfoSoapClient soapClient;
    private final CountryMapper mapper;
    private final MeterRegistry meters;

    public CountryService(
            CountryInfoRepository repository,
            CountryInfoSoapClient soapClient,
            CountryMapper mapper,
            MeterRegistry meters) {
        this.repository = repository;
        this.soapClient = soapClient;
        this.mapper = mapper;
        this.meters = meters;
    }

    /**
     * Normalise name -> SOAP ISO code -> SOAP full info -> persist.
     * Idempotent per ISO code. No DB transaction is held during SOAP calls.
     */
    public CountryResult onboard(String rawName) {
        String name = CountryNameNormalizer.normalize(rawName);
        log.info("Onboarding country requestedName={}", name);

        String isoCode;
        try {
            isoCode = soapClient.getCountryIsoCode(name);
        } catch (ExternalServiceException e) {
            Optional<CountryInfo> stored =
                    repository.findByNameIgnoreCase(name);

            if (stored.isPresent()) {
                log.warn("SOAP unavailable, serving stored country name={}", name);
                meters.counter("country.onboard", "outcome", "degraded").increment();
                return new CountryResult(
                        mapper.toResponse(stored.get()), false);
            }

            meters.counter(
                    "country.onboard", "outcome", "provider_unavailable").increment();
            throw e;
        }

        Optional<CountryInfo> existing = repository.findByIsoCode(isoCode);
        if (existing.isPresent()) {
            log.info("Country already stored isoCode={}", isoCode);
            meters.counter("country.onboard", "outcome", "existing").increment();
            return new CountryResult(
                    mapper.toResponse(existing.get()), false);
        }

        SoapCountryInfo info = soapClient.getFullCountryInfo(isoCode);

        try {
            CountryInfo saved = repository.save(
                    mapper.toEntity(info, isoCode));

            log.info("Country stored id={} isoCode={} languages={}",
                    saved.getId(), isoCode, saved.getLanguages().size());

            meters.counter("country.onboard", "outcome", "created").increment();
            return new CountryResult(mapper.toResponse(saved), true);

        } catch (DataIntegrityViolationException race) {
            log.info("Concurrent insert detected isoCode={}", isoCode);

            CountryInfo stored = repository.findByIsoCode(isoCode)
                    .orElseThrow(() -> race);

            meters.counter("country.onboard", "outcome", "existing").increment();
            return new CountryResult(mapper.toResponse(stored), false);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<CountryInfoResponse> findAll(int page, int size) {
        return PageResponse.of(
                repository.findAll(
                        PageRequest.of(page, size, Sort.by("name")))
                        .map(mapper::toResponse));
    }

    @Transactional(readOnly = true)
    public CountryInfoResponse findById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Transactional
    public CountryInfoResponse update(
            Long id, UpdateCountryRequest request) {
        CountryInfo country = load(id);
        mapper.applyUpdate(country, request);
        log.info("Country updated id={} isoCode={}",
                id, country.getIsoCode());

        return mapper.toResponse(repository.save(country));
    }

    @Transactional
    public void delete(Long id) {
        CountryInfo country = load(id);
        repository.delete(country);
        log.info("Country deleted id={} isoCode={}",
                id, country.getIsoCode());
    }

    private CountryInfo load(Long id) {
        return repository.findWithLanguagesById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Country with id " + id + " not found"));
    }
}
