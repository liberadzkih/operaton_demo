package com.devapo.operaton_demo.delegate;

import java.util.Properties;

import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.Expression;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/**
 * Sends an e-mail notification over SMTP.
 *
 * <p>This delegate is intentionally wired via {@code camunda:class} (not a
 * Spring bean) so that Camunda instantiates a fresh instance per execution and
 * the injected fields are thread-safe. All configuration — the full SMTP setup
 * and the target recipient — is provided through BPMN field injection, i.e. it
 * is set at the process level before deployment (see the placeholders in
 * {@code Operation-WeatherNotifier.bpmn20.xml}).</p>
 *
 * <p>Reads process variables {@code temperature} and {@code weatherFetchedAt}
 * (produced by {@link FetchWeatherDelegate}) to build the message body and
 * writes {@code emailSent}.</p>
 */
public class SendEmailDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendEmailDelegate.class);

    // Injected via camunda:field in the BPMN (placeholders set before deploy).
    private Expression smtpHost;
    private Expression smtpPort;
    private Expression smtpUsername;
    private Expression smtpPassword;
    private Expression fromAddress;
    private Expression recipientEmail;

    @Override
    public void execute(DelegateExecution execution) {
        String host = asString(smtpHost, execution);
        int port = asInt(smtpPort, execution, 587);
        String username = asString(smtpUsername, execution);
        String password = asString(smtpPassword, execution);
        String from = asString(fromAddress, execution);
        String recipient = asString(recipientEmail, execution);

        Object temperature = execution.getVariable("temperature");
        Object fetchedAt = execution.getVariable("weatherFetchedAt");

        String subject = "Weather update: " + temperature + " °C";
        String body = String.format(
                "Hello,%n%nThe current temperature is %s °C (as of %s).%n%n"
                        + "This is an automated notification sent every 10 minutes.%n",
                temperature, fetchedAt);

        LOGGER.info("Sending weather e-mail to {} via {}:{}", recipient, host, port);

        try {
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(host);
            mailSender.setPort(port);
            mailSender.setUsername(username);
            mailSender.setPassword(password);

            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);

            execution.setVariable("emailSent", true);
            LOGGER.info("Weather e-mail sent to {}", recipient);

        } catch (Exception e) {
            execution.setVariable("emailSent", false);
            LOGGER.error("Failed to send weather e-mail to {}: {}", recipient, e.getMessage());
            throw new BpmnError("EMAIL_ERROR", "Failed to send e-mail: " + e.getMessage());
        }
    }

    private String asString(Expression expression, DelegateExecution execution) {
        if (expression == null) {
            return null;
        }
        Object value = expression.getValue(execution);
        return value != null ? value.toString() : null;
    }

    private int asInt(Expression expression, DelegateExecution execution, int fallback) {
        String value = asString(expression, execution);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid SMTP port '{}', falling back to {}", value, fallback);
            return fallback;
        }
    }

    // Setters required for Camunda field injection.

    public void setSmtpHost(Expression smtpHost) {
        this.smtpHost = smtpHost;
    }

    public void setSmtpPort(Expression smtpPort) {
        this.smtpPort = smtpPort;
    }

    public void setSmtpUsername(Expression smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public void setSmtpPassword(Expression smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public void setFromAddress(Expression fromAddress) {
        this.fromAddress = fromAddress;
    }

    public void setRecipientEmail(Expression recipientEmail) {
        this.recipientEmail = recipientEmail;
    }
}
