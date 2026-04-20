package com.smarttest.manager.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "polling")
public class PollingConfig {

    private int intervalSeconds = 5;
    private int timeoutMinutes = 10;
}
