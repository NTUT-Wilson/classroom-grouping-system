package com.classroomgrouping.platform;

import com.classroomgrouping.grouping.api.DueWork;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class SchedulerController {
    private final DueWork dueWork;

    SchedulerController(DueWork dueWork) { this.dueWork = dueWork; }

    @PostMapping("/internal/scheduler/tick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void tick() { dueWork.processDueWork(); }
}
