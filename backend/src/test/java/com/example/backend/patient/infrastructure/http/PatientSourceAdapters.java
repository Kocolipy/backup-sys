package com.example.backend.patient.infrastructure.http;

import com.example.backend.patient.domain.PatientStore;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.springframework.web.client.RestClient;

/**
 * The production patient port, for the tests that read the patient source's mock: each adapter
 * counts an answer's invalid fields in a registry of its own, so a test that asserts the count
 * builds the adapter on a registry it holds.
 */
public final class PatientSourceAdapters {

    private PatientSourceAdapters() {
    }

    /** The HTTP adapter, reading the patient source at {@code baseUrl}. */
    public static PatientStore reading(String baseUrl) {
        return at(baseUrl);
    }

    /** The HTTP adapter on the source at {@code baseUrl}, under the default timeouts. */
    static PatientSourceAdapter at(String baseUrl) {
        return new PatientSourceAdapter(RestClient.builder(), baseUrl, new SimpleMeterRegistry());
    }

    /** The HTTP adapter on the source at {@code baseUrl}, under the timeouts given. */
    static PatientSourceAdapter at(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        return new PatientSourceAdapter(RestClient.builder(), baseUrl, connectTimeout, readTimeout,
                new SimpleMeterRegistry());
    }
}
