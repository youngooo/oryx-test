package org.oryxos.core.model;

import java.time.ZoneId;
import org.springframework.scheduling.support.CronExpression;

public record ScheduleDefinition(
        String id,
        String cron,
        ZoneId zoneId,
        String prompt,
        boolean enabled) {

    public ScheduleDefinition {
        id = Profile.requireText(id, "schedule id");
        cron = Profile.requireText(cron, "cron");
        if (!CronExpression.isValidExpression(cron)) {
            throw new IllegalArgumentException("Invalid cron expression");
        }
        zoneId = zoneId == null ? ZoneId.systemDefault() : zoneId;
        prompt = Profile.requireText(prompt, "schedule prompt");
    }
}
