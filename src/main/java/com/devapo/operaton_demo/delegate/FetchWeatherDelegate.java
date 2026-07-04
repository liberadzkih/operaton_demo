package com.devapo.operaton_demo.delegate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches the current temperature from the free Open-Meteo API
 * (https://open-meteo.com), which requires no API key.
 *
 * <p>Reads process variables {@code latitude} and {@code longitude}
 * (defaulting to Warsaw) and writes {@code temperature} and
 * {@code weatherFetchedAt}.</p>
 */
@Component("fetchWeatherDelegate")
public class FetchWeatherDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchWeatherDelegate.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void execute(DelegateExecution execution) {
        double latitude = toDouble(execution.getVariable("latitude"), 52.23);   // Warsaw
        double longitude = toDouble(execution.getVariable("longitude"), 21.01);

        LOGGER.info("Fetching weather for lat={}, lon={}", latitude, longitude);

        String url = String.format(
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s&current=temperature_2m",
                latitude, longitude);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new BpmnError("WEATHER_ERROR",
                        "Open-Meteo API returned status " + response.statusCode());
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode tempNode = root.path("current").path("temperature_2m");
            if (tempNode.isMissingNode()) {
                throw new BpmnError("WEATHER_ERROR", "No temperature returned by Open-Meteo API");
            }

            double temperature = tempNode.asDouble();
            String fetchedAt = root.path("current").path("time").asText();

            execution.setVariable("temperature", temperature);
            execution.setVariable("weatherFetchedAt", fetchedAt);

            LOGGER.info("Current temperature at ({}, {}): {} °C (as of {})",
                    latitude, longitude, temperature, fetchedAt);

        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch weather", e);
            throw new BpmnError("WEATHER_ERROR", "Failed to call Open-Meteo API: " + e.getMessage());
        }
    }

    private double toDouble(Object value, double fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(value.toString().trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
