package com.devapo.operaton_demo.config;

import org.operaton.bpm.engine.impl.cfg.AbstractProcessEnginePlugin;
import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Optional additional Operaton configuration, applied as a process engine plugin.
 * Tunes the job executor so that asynchronous work (e.g. the simulated
 * processing) is picked up quickly and enables engine metrics.
 */
@Component
public class OperatonConfig extends AbstractProcessEnginePlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger(OperatonConfig.class);

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        LOGGER.info("Applying custom Operaton process engine configuration");

        configuration.setJobExecutorActivate(true);
        configuration.setMetricsEnabled(true);
    }
}
