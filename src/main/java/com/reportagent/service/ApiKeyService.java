package com.reportagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reportagent.entity.ApiKeyConfig;
import com.reportagent.mapper.ApiKeyConfigMapper;
import com.reportagent.util.AesUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ApiKeyService {

    private final ApiKeyConfigMapper mapper;
    private final AesUtil aesUtil;

    public ApiKeyService(ApiKeyConfigMapper mapper, AesUtil aesUtil) {
        this.mapper = mapper;
        this.aesUtil = aesUtil;
    }

    public List<ApiKeyConfig> list() {
        List<ApiKeyConfig> list = mapper.selectList(null);
        list.forEach(c -> c.setApiKey(null)); // never expose raw key
        return list;
    }

    public ApiKeyConfig getActive() {
        ApiKeyConfig config = mapper.selectOne(
                new LambdaQueryWrapper<ApiKeyConfig>()
                        .eq(ApiKeyConfig::getIsActive, true));
        if (config != null) {
            config.setApiKey(aesUtil.decrypt(config.getApiKey()));
        }
        return config;
    }

    public ApiKeyConfig create(ApiKeyConfig config) {
        config.setApiKey(aesUtil.encrypt(config.getApiKey()));
        config.setIsActive(config.getIsActive() != null && config.getIsActive());
        if (config.getIsActive()) {
            deactivateOthers(null);
        }
        mapper.insert(config);
        config.setApiKey(null);
        return config;
    }

    public ApiKeyConfig update(Long id, ApiKeyConfig config) {
        config.setId(id);
        if (config.getApiKey() != null && !config.getApiKey().isEmpty()) {
            config.setApiKey(aesUtil.encrypt(config.getApiKey()));
        } else {
            config.setApiKey(null);
        }
        if (Boolean.TRUE.equals(config.getIsActive())) {
            deactivateOthers(id);
        }
        mapper.updateById(config);
        config.setApiKey(null);
        return config;
    }

    public boolean delete(Long id) {
        return mapper.deleteById(id) > 0;
    }

    public boolean activate(Long id) {
        deactivateOthers(id);
        ApiKeyConfig config = new ApiKeyConfig();
        config.setId(id);
        config.setIsActive(true);
        return mapper.updateById(config) > 0;
    }

    private void deactivateOthers(Long excludeId) {
        List<ApiKeyConfig> actives = mapper.selectList(
                new LambdaQueryWrapper<ApiKeyConfig>()
                        .eq(ApiKeyConfig::getIsActive, true));
        for (ApiKeyConfig c : actives) {
            if (excludeId == null || !c.getId().equals(excludeId)) {
                c.setIsActive(false);
                mapper.updateById(c);
            }
        }
    }
}
