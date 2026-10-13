package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.grouping.api.DueWork;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Configuration
class DueWorkConfiguration {
    @Bean
    @ConditionalOnMissingBean(DueWork.class)
    DueWork unimplementedDueWork() {
        return () -> { throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                "Deadline allocation is not implemented in the foundation"); };
    }
}
