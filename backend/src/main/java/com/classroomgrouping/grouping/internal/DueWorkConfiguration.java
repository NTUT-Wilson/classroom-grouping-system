package com.classroomgrouping.grouping.internal;

import com.classroomgrouping.grouping.api.DueWork;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
class DueWorkConfiguration {
    @Bean
    @ConditionalOnMissingBean(DueWork.class)
    DueWork databaseDueWork(JdbcTemplate jdbc, PlatformTransactionManager transactions) {
        return new DatabaseDueWork(jdbc, transactions);
    }
}
