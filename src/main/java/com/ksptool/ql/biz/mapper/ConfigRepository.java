package com.ksptool.ql.biz.mapper;

import com.ksptool.ql.biz.model.po.ConfigPo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigRepository extends JpaRepository<ConfigPo, Long> {
    
    ConfigPo findByConfigKey(String configKey);
    
    ConfigPo findByUserIdAndConfigKey(Long userId, String configKey);
    
    boolean existsByConfigKey(String configKey);
    
    boolean existsByUserIdAndConfigKey(Long userId, String configKey);
} 