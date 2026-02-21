package com.game.playforge.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway修复配置
 * <p>
 * 在执行migrate之前先执行repair，自动修复以下场景：
 * - 上次迁移失败（success=0）的记录
 * - 迁移文件checksum变更（开发阶段调整SQL内容）
 * </p>
 *
 * @author Richard Zhang
 * @since 1.0
 */
@Configuration
public class FlywayRepairConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayRepairConfig.class);

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            log.info("执行Flyway repair（修复失败记录和checksum变更）...");
            flyway.repair();
            log.info("执行Flyway migrate...");
            flyway.migrate();
        };
    }
}
