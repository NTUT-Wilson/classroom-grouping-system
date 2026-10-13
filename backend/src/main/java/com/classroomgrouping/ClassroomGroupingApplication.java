package com.classroomgrouping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClassroomGroupingApplication {
    public static void main(String[] args) {
        SpringApplication.run(ClassroomGroupingApplication.class, args);
    }
}
