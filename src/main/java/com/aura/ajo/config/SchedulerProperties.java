package com.aura.ajo.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "savings.scheduler")
@Getter
@Setter
public class SchedulerProperties {

    private DefaultDetection defaultDetection = new DefaultDetection();

    @Getter
    @Setter
    public static class DefaultDetection {
        /** Set false to disable the job without removing the cron expression. */
        private boolean enabled = true;
        /** Cron expression controlling how often the job fires. */
        private String cron = "0 */15 * * * *";
        /**
         * Days before periodEnd to fire the CONTRIBUTION_DUE_SOON notification.
         * E.g. 2 means notify members two days before their deadline.
         */
        private int dueSoonDays = 2;
    }
}