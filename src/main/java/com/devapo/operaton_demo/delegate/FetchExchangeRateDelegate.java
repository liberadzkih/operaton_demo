package com.devapo.operaton_demo.delegate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fetches a currency exchange rate from the free Frankfurter API
 * (https://www.frankfurter.app), which serves European Central Bank rates
 * and requires no API key.
 *
 * <p>Reads process variables {@code amount}, {@code fromCurrency},
 * {@code toCurrency} and writes {@code exchangeRate}, {@code convertedAmount}
 * and {@code rateDate}.</p>
 */
@Component("fetchExchangeRateDelegate")
public class FetchExchangeRateDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(FetchExchangeRateDelegate.class);

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void execute(DelegateExecution execution) {
        double amount = toDouble(execution.getVariable("amount"), 1.0);
        String from = toCurrency(execution.getVariable("fromCurrency"), "USD");
        String to = toCurrency(execution.getVariable("toCurrency"), "EUR");

        LOGGER.info("Fetching exchange rate for {} {} -> {}", amount, from, to);

        String url = String.format(
                "https://api.frankfurter.dev/v1/latest?amount=%s&from=%s&to=%s",
                amount, from, to);

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
                throw new BpmnError("EXCHANGE_RATE_ERROR",
                        "Frankfurter API returned status " + response.statusCode());
            }

            JsonNode root = MAPPER.readTree(response.body());
            JsonNode rateNode = root.path("rates").path(to);
            if (rateNode.isMissingNode()) {
                throw new BpmnError("EXCHANGE_RATE_ERROR",
                        "No rate returned for currency " + to);
            }

            double convertedAmount = rateNode.asDouble();
            double exchangeRate = amount != 0 ? convertedAmount / amount : 0;
            String rateDate = root.path("date").asText();

            execution.setVariable("exchangeRate", exchangeRate);
            execution.setVariable("convertedAmount", convertedAmount);
            execution.setVariable("rateDate", rateDate);

            // Human-readable summary for the review task form (string type,
            // so it renders safely in a generated Camunda task form).
            String rateSummary = String.format("%s %s = %s %s (rate %s, as of %s)",
                    amount, from, convertedAmount, to, exchangeRate, rateDate);
            execution.setVariable("rateSummary", rateSummary);

            LOGGER.info("Rate {} {}/{} on {}: {} {} = {} {}",
                    exchangeRate, from, to, rateDate, amount, from, convertedAmount, to);

        } catch (BpmnError e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch exchange rate", e);
            throw new BpmnError("EXCHANGE_RATE_ERROR", "Failed to call Frankfurter API: " + e.getMessage());
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

    private String toCurrency(Object value, String fallback) {
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return value.toString().trim().toUpperCase();
    }
}
