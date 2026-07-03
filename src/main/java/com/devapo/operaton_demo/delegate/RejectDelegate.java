package com.devapo.operaton_demo.delegate;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Uses the default Spring bean name derived from the class name:
 * {@code rejectDelegate}.
 */
@Component
public class RejectDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RejectDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        LOGGER.info("Process rejected");

        Object email = execution.getVariable("email");
        LOGGER.info("Rejection notification sent to: {}", email);

        execution.setVariable("rejectionNotificationSent", true);
    }
}
