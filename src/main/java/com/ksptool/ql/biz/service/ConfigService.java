package com.ksptool.ql.biz.service;

import com.ksptool.ql.biz.mapper.ConfigRepository;
import com.ksptool.ql.biz.model.po.ConfigPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class ConfigService {
    
    private final ConfigRepository configRepository;
    
    public void saveConfig(ConfigPo config) {
        if (config.getUserId() == null) {
            config.setUserId(-1L);
        }
        config.setCreateTime(new Date());
        config.setUpdateTime(new Date());
        configRepository.save(config);
    }
    
    public ConfigPo getConfigByKey(String configKey) {
        return getConfigByKey(configKey, -1L);
    }
    
    public ConfigPo getConfigByKey(String configKey, Long userId) {
        return configRepository.findByUserIdAndConfigKey(userId, configKey);
    }
    
    public String getConfigValue(String key) {
        return getConfigValue(key, -1L);
    }
    
    public String getConfigValue(String key, Long userId) {
        ConfigPo config = configRepository.findByUserIdAndConfigKey(userId, key);
        if (config == null && userId != -1L) {
            // 如果用户配置不存在，尝试获取全局配置
            config = configRepository.findByUserIdAndConfigKey(-1L, key);
        }
        return config != null ? config.getConfigValue() : null;
    }
    
    public void setConfigValue(String key, String value) {
        setConfigValue(key, value, -1L);
    }
    
    public void setConfigValue(String key, String value, Long userId) {
        ConfigPo config = configRepository.findByUserIdAndConfigKey(userId, key);
        if (config == null) {
            config = new ConfigPo();
            config.setConfigKey(key);
            config.setUserId(userId);
            config.setCreateTime(new Date());
        }
        config.setConfigValue(value);
        config.setUpdateTime(new Date());
        configRepository.save(config);
    }
    
    public void updateConfig(ConfigPo config) {
        if (config.getUserId() == null) {
            config.setUserId(-1L);
        }
        ConfigPo existingConfig = configRepository.findByUserIdAndConfigKey(config.getUserId(), config.getConfigKey());
        if (existingConfig != null) {
            existingConfig.setConfigValue(config.getConfigValue());
            existingConfig.setDescription(config.getDescription());
            existingConfig.setUpdateTime(new Date());
            configRepository.save(existingConfig);
        }
    }
    
    public void deleteConfig(String configKey) {
        deleteConfig(configKey, -1L);
    }
    
    public void deleteConfig(String configKey, Long userId) {
        ConfigPo config = configRepository.findByUserIdAndConfigKey(userId, configKey);
        if (config != null) {
            configRepository.delete(config);
        }
    }
    
    public boolean existsConfigKey(String configKey) {
        return existsConfigKey(configKey, -1L);
    }
    
    public boolean existsConfigKey(String configKey, Long userId) {
        return configRepository.existsByUserIdAndConfigKey(userId, configKey);
    }
} 