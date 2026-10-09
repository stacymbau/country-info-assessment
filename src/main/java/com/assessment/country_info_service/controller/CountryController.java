package com.assessment.country_info_service.controller;

import com.assessment.country_info_service.dto.CountryInfoResponse;
import com.assessment.country_info_service.dto.CountryRequest;
import com.assessment.country_info_service.dto.CountryResult;
import com.assessment.country_info_service.dto.PageResponse;
import com.assessment.country_info_service.dto.UpdateCountryRequest;
import com.assessment.country_info_service.service.CountryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/countries")
@Validated
public class CountryController {

    private final CountryService service;

    public CountryController(CountryService service) {
        this.service = service;
    }

    /** 201 when a new country was stored, 200 when it already existed. */
    @PostMapping
    public ResponseEntity<CountryInfoResponse> onboard(@Valid @RequestBody CountryRequest request) {
        CountryResult result = service.onboard(request.name());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(result.body().id()).toUri();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(location)
                .body(result.body());
    }

    @GetMapping
    public PageResponse<CountryInfoResponse> getAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.findAll(page, size);
    }

    @GetMapping("/{id}")
    public CountryInfoResponse getById(@PathVariable Long id) {
        return service.findById(id);
    }

    @PutMapping("/{id}")
    public CountryInfoResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCountryRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
