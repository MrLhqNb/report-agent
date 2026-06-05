package com.reportagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.reportagent.mapper")
@EnableScheduling
public class ReportAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportAgentApplication.class, args);
    }
}
