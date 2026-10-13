package com.classroomgrouping.platform;

import com.classroomgrouping.grouping.api.DueWork;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
class LocalScheduler {
    private final DueWork dueWork;
    LocalScheduler(DueWork dueWork) { this.dueWork = dueWork; }

    @EventListener(ApplicationReadyEvent.class)
    void catchUp() { dueWork.processDueWork(); }

    @Scheduled(fixedDelayString = "${app.scheduler.interval-ms:60000}")
    void reconcile() { dueWork.processDueWork(); }
}
