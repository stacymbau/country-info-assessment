package com.assessment.country_info_service.client;

import com.assessment.country_info_service.config.SoapProperties;
import com.assessment.country_info_service.exception.ExternalServiceException;
import com.assessment.country_info_service.exception.SoapTransportException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.HtmlUtils;

@Component
public class CountryInfoSoapClient {

    private static final Logger log = LoggerFactory.getLogger(CountryInfoSoapClient.class);
    private static final String NS = "http://www.oorsprong.org/websamples.countryinfo";
    private static final MediaType TEXT_XML_UTF8 = new MediaType("text", "xml", StandardCharsets.UTF_8);

    private final RestClient restClient;
    private final String url;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public CountryInfoSoapClient(SoapProperties props, CircuitBreaker soapCircuitBreaker, Retry soapRetry) {
        HttpClient http = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(props.connectTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(props.readTimeout());
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.url = props.url();
        this.circuitBreaker = soapCircuitBreaker;
        this.retry = soapRetry;
    }

    @Cacheable(cacheNames = "isoCodes", key = "#countryName")
    public String getCountryIsoCode(String countryName) {
        return guarded("CountryISOCode", () -> SoapXmlParser.parseIsoCode(
                call("CountryISOCode", envelope("CountryISOCode", "sCountryName", countryName))));
    }

    @Cacheable(cacheNames = "countryInfo", key = "#isoCode")
    public SoapCountryInfo getFullCountryInfo(String isoCode) {
        return guarded("FullCountryInfo", () -> SoapXmlParser.parseFullCountryInfo(
                call("FullCountryInfo", envelope("FullCountryInfo", "sCountryISOCode", isoCode))));
    }

    /** Retry (outer) around circuit breaker (inner). CountryNotFoundException passes through untouched. */
    private <T> T guarded(String operation, Supplier<T> call) {
        Supplier<T> decorated = Retry.decorateSupplier(retry, CircuitBreaker.decorateSupplier(circuitBreaker, call));
        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            log.warn("SOAP circuit open, failing fast operation={}", operation);
            throw new ExternalServiceException("Country information provider is temporarily unavailable", e);
        } catch (ExternalServiceException e) {
            log.error("SOAP call failed after retries operation={} error={}", operation, e.getMessage());
            throw e;
        }
    }

    private String call(String operation, String requestXml) {
        long start = System.nanoTime();
        try {
            String response = restClient.post()
                    .uri(url)
                    .contentType(TEXT_XML_UTF8)
                    .accept(MediaType.TEXT_XML, MediaType.APPLICATION_XML)
                    .header("SOAPAction", "")
                    .body(requestXml)
                    .retrieve()
                    .body(String.class);
            if (response == null || response.isBlank()) {
                throw new SoapTransportException("Empty SOAP response for " + operation, null);
            }
            log.info("SOAP call ok operation={} durationMs={}", operation, (System.nanoTime() - start) / 1_000_000);
            return response;
        } catch (RestClientException e) {
            log.warn("SOAP call error operation={} durationMs={} error={}", operation,
                    (System.nanoTime() - start) / 1_000_000, e.getMessage());
            throw new SoapTransportException(operation + " call failed: " + e.getMessage(), e);
        }
    }

    private static String envelope(String operation, String param, String value) {
        return """
                <?xml version="1.0" encoding="utf-8"?>
                <soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/">
                  <soap:Body>
                    <web:%1$s xmlns:web="%2$s">
                      <web:%3$s>%4$s</web:%3$s>
                    </web:%1$s>
                  </soap:Body>
                </soap:Envelope>""".formatted(operation, NS, param, HtmlUtils.htmlEscape(value));
    }
}