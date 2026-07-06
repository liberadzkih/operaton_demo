package com.devapo.operaton_demo.delegate;

import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Uses the default Spring bean name derived from the class name:
 * {@code sendNotificationDelegate}.
 */
@Component
public class SendNotificationDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(SendNotificationDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        LOGGER.info("Sending notification");

        Object email = execution.getVariable("email");
        LOGGER.info("Notification sent to: {}", email);

        execution.setVariable("notificationSent", true);
    }
}
