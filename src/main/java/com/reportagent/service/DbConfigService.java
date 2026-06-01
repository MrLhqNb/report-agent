package com.reportagent.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.reportagent.dto.DbConfigDTO;
import com.reportagent.entity.DbConfig;

public interface DbConfigService extends IService<DbConfig> {
    DbConfig create(DbConfigDTO dto);
    DbConfig update(Long id, DbConfigDTO dto);
    boolean testConnection(DbConfigDTO dto);
}
