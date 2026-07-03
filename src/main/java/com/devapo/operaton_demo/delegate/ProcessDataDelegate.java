package com.devapo.operaton_demo.delegate;

import java.time.LocalDateTime;

import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("processDataDelegate")
public class ProcessDataDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessDataDelegate.class);

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        LOGGER.info("Processing data");

        // Simulate some work
        Thread.sleep(1000);

        execution.setVariable("processed", true);
        execution.setVariable("processedAt", LocalDateTime.now().toString());

        LOGGER.info("Data processed successfully at {}", execution.getVariable("processedAt"));
    }
}
