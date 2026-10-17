package com.classroomgrouping.grouping.api;

/** Provider-independent, bounded trigger for durable deadline processing and recovery. */
public interface DueWork {
    void processDueWork();
}
