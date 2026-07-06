package com.devapo.operaton_demo.delegate;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.bpm.engine.delegate.DelegateExecution;
import org.operaton.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component("validateDataDelegate")
public class ValidateDataDelegate implements JavaDelegate {

    private static final Logger LOGGER = LoggerFactory.getLogger(ValidateDataDelegate.class);

    @Override
    public void execute(DelegateExecution execution) {
        LOGGER.info("Validating data");

        Object data = execution.getVariable("data");
        boolean valid = data != null && !data.toString().isBlank();

        execution.setVariable("validationResult", valid);
        LOGGER.info("Validation result for data [{}]: {}", data, valid);

        if (!valid) {
            LOGGER.warn("Data validation failed - throwing BpmnError");
            throw new BpmnError("VALIDATION_ERROR", "Data is not valid");
        }
    }
}
