package com.reportagent.dto;

import lombok.Data;

@Data
public class DbConfigDTO {
    private Long id;
    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
}
