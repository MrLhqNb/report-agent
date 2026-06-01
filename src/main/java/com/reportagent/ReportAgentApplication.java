package com.reportagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.reportagent.mapper")
public class ReportAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(ReportAgentApplication.class, args);
    }
}
