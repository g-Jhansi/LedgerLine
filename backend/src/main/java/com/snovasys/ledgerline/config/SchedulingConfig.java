package com.snovasys.ledgerline.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled task execution.
 * Active only when NOT running in test profile.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}

